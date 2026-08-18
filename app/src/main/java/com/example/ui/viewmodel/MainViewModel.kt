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
    val selectedRange: String = "",
    val isServiceRunning: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val accountsHistory: List<AccountEntity> = emptyList(),
    val isLoadingRanges: Boolean = false,
    val isCreatingAccount: Boolean = false,
    val nameLanguage: String = "BANGLA",
    val genderConfig: String = "FEMALE",
    val ageFilter: String = "18+",
    val telegramChatId: String = "",
    val telegramUsername: String = "",
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
        val username = repository.getTelegramUsername()

        _uiState.value = _uiState.value.copy(
            savedPassword = pass,
            selectedRange = range,
            isServiceRunning = active,
            nameLanguage = lang,
            genderConfig = gender,
            ageFilter = age,
            telegramChatId = chatId,
            telegramUsername = username
        )

        refreshFacebookRanges()
    }

    fun setTelegramBotConfig(chatId: String, username: String) {
        val cleanChatId = chatId.trim()
        val cleanUser = when {
            username.isBlank() -> ""
            username.startsWith("@") -> username.trim()
            else -> "@${username.trim()}"
        }
        repository.setTelegramChatId(cleanChatId)
        repository.setTelegramUsername(cleanUser)
        _uiState.value = _uiState.value.copy(
            telegramChatId = cleanChatId,
            telegramUsername = cleanUser
        )
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

    private val isCreatingInViewModel = java.util.concurrent.atomic.AtomicBoolean(false)

    fun createAccountNow(context: Context? = null) {
        if (!isCreatingInViewModel.compareAndSet(false, true)) {
            _uiState.value = _uiState.value.copy(statusMessage = "⚠️ Account creation is already in progress...")
            return
        }

        val range = _uiState.value.selectedRange
        if (range.isBlank()) {
            isCreatingInViewModel.set(false)
            _uiState.value = _uiState.value.copy(
                isCreatingAccount = false,
                statusMessage = "❌ No live range selected! Please refresh ranges."
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isCreatingAccount = true,
                    statusMessage = "⏳ [1/3] Fetching number from live range $range..."
                )

                val phone = repository.fetchNumber(range)
                if (phone.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isCreatingAccount = false,
                        statusMessage = "❌ FAILED!\nNo live number available for range $range"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    statusMessage = "📱 [2/3] Number Received: $phone\n⚡ Registering Facebook Account..."
                )

                val result = repository.createAccountForNumber(phone, range)

                if (result.success && context != null) {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("UID", result.uid)
                        clipboard.setPrimaryClip(clip)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val finalStatus = if (result.success) {
                    "✅ [3/3] ACCOUNT CREATED!\nUID: ${result.uid} (Auto Copied)\nNumber: ${result.phone}\nName: ${result.name}"
                } else {
                    "❌ CREATION FAILED!\nNumber: ${result.phone}\nError: ${result.error.ifEmpty { "Registration rejected" }}"
                }

                _uiState.value = _uiState.value.copy(
                    isCreatingAccount = false,
                    statusMessage = finalStatus
                )
            } finally {
                isCreatingInViewModel.set(false)
            }
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
