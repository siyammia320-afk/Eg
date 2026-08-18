package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.model.AccountEntity
import com.example.repository.AppRepository
import com.example.service.FloatingOverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val savedPassword: String = "",
    val facebookRanges: List<String> = emptyList(),
    val selectedRange: String = "8801XXX",
    val isServiceRunning: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val accountsHistory: List<AccountEntity> = emptyList(),
    val isLoadingRanges: Boolean = false,
    val isCreatingAccount: Boolean = false,
    val nameLanguage: String = "BANGLA",
    val genderConfig: String = "FEMALE",
    val ageFilter: String = "18+",
    val telegramChatId: String = "",
    val statusMessage: String? = null
)

class MainViewModel(
    private val repository: AppRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val autoCopiedOtps = mutableSetOf<String>()

    init {
        loadInitialData()
        observeAccounts()
        startAutomaticOtpPolling()
    }

    private fun startAutomaticOtpPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val otps = repository.checkAndProcessOtps()
                    for (otp in otps) {
                        val key = "${otp.number}_${otp.otpCode}"
                        if (!autoCopiedOtps.contains(key)) {
                            autoCopiedOtps.add(key)
                            appContext?.let { ctx ->
                                try {
                                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("OTP Code", otp.otpCode)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(ctx, "OTP Auto Copied: ${otp.otpCode}", android.widget.Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(2500)
            }
        }
    }

    private fun loadInitialData() {
        val pass = repository.getSavedPassword()
        val range = repository.getSelectedRange()
        val active = repository.isServiceActive()
        val lang = repository.getNameLanguage()
        val gender = repository.getGenderConfig()
        val age = repository.getAgeFilter()
        val chatId = repository.getTelegramChatId()

        _uiState.value = _uiState.value.copy(
            savedPassword = pass,
            selectedRange = range,
            isServiceRunning = active,
            nameLanguage = lang,
            genderConfig = gender,
            ageFilter = age,
            telegramChatId = chatId
        )

        refreshFacebookRanges()
    }

    fun setTelegramChatId(chatId: String) {
        repository.setTelegramChatId(chatId)
        _uiState.value = _uiState.value.copy(telegramChatId = chatId)
    }

    fun setNameLanguage(lang: String) {
        repository.setNameLanguage(lang)
        _uiState.value = _uiState.value.copy(nameLanguage = lang)
    }

    fun setGenderConfig(gender: String) {
        repository.setGenderConfig(gender)
        _uiState.value = _uiState.value.copy(genderConfig = gender)
    }

    fun setAgeFilter(age: String) {
        repository.setAgeFilter(age)
        _uiState.value = _uiState.value.copy(ageFilter = age)
    }

    private fun observeAccounts() {
        viewModelScope.launch {
            repository.allAccounts.collectLatest { list ->
                _uiState.value = _uiState.value.copy(accountsHistory = list)
            }
        }
    }

    fun checkPermissions(context: Context) {
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        _uiState.value = _uiState.value.copy(isOverlayPermissionGranted = overlayGranted)
    }

    fun savePassword(password: String) {
        repository.setSavedPassword(password)
        _uiState.value = _uiState.value.copy(
            savedPassword = password,
            statusMessage = "Password saved successfully!"
        )
    }

    fun refreshFacebookRanges() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRanges = true)
            val ranges = repository.getLiveFacebookRanges()
            val currentSelected = _uiState.value.selectedRange
            val newSelected = if (ranges.contains(currentSelected)) currentSelected else (ranges.firstOrNull() ?: "")

            repository.setSelectedRange(newSelected)

            _uiState.value = _uiState.value.copy(
                facebookRanges = ranges,
                selectedRange = newSelected,
                isLoadingRanges = false
            )
        }
    }

    fun selectRange(range: String) {
        repository.setSelectedRange(range)
        _uiState.value = _uiState.value.copy(selectedRange = range)
    }

    fun toggleService(context: Context) {
        val currentState = _uiState.value.isServiceRunning
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        if (!overlayGranted) {
            _uiState.value = _uiState.value.copy(statusMessage = "Please grant Display Overlay permission first!")
            return
        }

        if (currentState) {
            stopFloatingService(context)
        } else {
            startFloatingService(context)
        }
    }

    fun startFloatingService(context: Context) {
        val intent = Intent(context, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        repository.setServiceActive(true)
        _uiState.value = _uiState.value.copy(
            isServiceRunning = true,
            statusMessage = "Start pressed: Floating Overlay is ACTIVE!"
        )
    }

    fun stopFloatingService(context: Context) {
        val intent = Intent(context, FloatingOverlayService::class.java)
        context.stopService(intent)
        repository.setServiceActive(false)
        _uiState.value = _uiState.value.copy(
            isServiceRunning = false,
            statusMessage = "Stop pressed: Floating Overlay disabled."
        )
    }

    fun createAccountNow(context: Context? = null) {
        val range = _uiState.value.selectedRange
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingAccount = true, statusMessage = "Fetching number from $range...")
            val phone = repository.fetchNumber(range)
            if (phone.isNullOrEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isCreatingAccount = false,
                    statusMessage = "FAILED!\nNo number found for $range"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(statusMessage = "Number Received: $phone\nCreating account...")
            val result = repository.createAccountForNumber(phone, range)

            if (result.success && context != null) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("UID", result.uid)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "Account Created! c_user UID Copied: ${result.uid}", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _uiState.value = _uiState.value.copy(
                isCreatingAccount = false,
                statusMessage = if (result.success) "SUCCESSFUL!\nAuto Copied c_user UID: ${result.uid}\nNumber: ${result.phone}" else "FAILED!\nNumber: ${result.phone}\n${result.error.ifEmpty { "Creation failed" }}"
            )
        }
    }

    fun checkOtpsNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusMessage = "Checking OTPs...")
            val otps = repository.checkAndProcessOtps()
            _uiState.value = _uiState.value.copy(
                statusMessage = if (otps.isNotEmpty()) "${otps.size} OTP(s) Received!" else "No new OTPs found."
            )
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            repository.deleteAccountById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllAccounts()
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }
}

class MainViewModelFactory(
    private val repository: AppRepository,
    private val context: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
