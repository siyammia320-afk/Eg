package com.example.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.NetworkClient
import com.example.model.AccountEntity
import com.example.model.RangeItem
import com.example.repository.AppRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MainUiState(
    val customPassword: String = "arafat@@##",
    val facebookRanges: List<RangeItem> = emptyList(),
    val rangeMessages: Map<String, String> = emptyMap(),
    val selectedRange: String = "",
    val accountsHistory: List<AccountEntity> = emptyList(),
    val isLoadingRanges: Boolean = false,
    val isCreatingAccount: Boolean = false,
    val apiKey: String = "",
    val statusMessage: String? = null,
    val terminalLogs: List<String> = emptyList(),
    val manualCheckPhone: String = "",
    val manualCheckResult: String? = null,
    val isCheckingManualPhone: Boolean = false
)

class MainViewModel(
    private val repository: AppRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val autoCopiedOtps = mutableSetOf<String>()

    init {
        com.example.security.ApiSecurityManager.enforceIntegrityOrExit(null)
        loadInitialData()
        observeAccounts()
        startAutomaticOtpPolling()
    }

    private fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$time] $message"
        val current = _uiState.value.terminalLogs.toMutableList()
        current.add(formatted)
        if (current.size > 150) {
            _uiState.value = _uiState.value.copy(terminalLogs = current.takeLast(100))
        } else {
            _uiState.value = _uiState.value.copy(terminalLogs = current)
        }
    }

    private fun startAutomaticOtpPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    // 1. Process OTP auto-copy for user accounts
                    val otps = repository.checkAndProcessOtps()
                    for (otp in otps) {
                        val key = "${otp.number}_${otp.otpCode}"
                        if (!autoCopiedOtps.contains(key)) {
                            autoCopiedOtps.add(key)
                            addLog("⚡ NEW OTP INTERCEPTED: +${otp.number} -> ${otp.otpCode}")
                            appContext?.let { ctx ->
                                try {
                                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("OTP Code", otp.otpCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(ctx, "OTP Auto Copied: ${otp.otpCode}", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    // 2. Fetch live hits from GET /console to attach messages to every range
                    val currentRanges = _uiState.value.facebookRanges
                    val newMessages = _uiState.value.rangeMessages.toMutableMap()
                    var hasNewMessages = false

                    val consoleHits = repository.getConsoleHits()
                    for (hit in consoleHits) {
                        val cleanCode = hit.range.trim()
                        if (cleanCode.isNotBlank() && hit.message.isNotBlank()) {
                            if (newMessages[cleanCode] != hit.message) {
                                newMessages[cleanCode] = hit.message
                                hasNewMessages = true
                            }
                            for (range in currentRanges) {
                                val prefix = range.code.replace("X", "", ignoreCase = true).trim()
                                if (prefix.isNotEmpty() && cleanCode.startsWith(prefix)) {
                                    if (newMessages[range.code] != hit.message) {
                                        newMessages[range.code] = hit.message
                                        hasNewMessages = true
                                    }
                                }
                            }
                        }
                    }

                    if (hasNewMessages) {
                        _uiState.value = _uiState.value.copy(rangeMessages = newMessages)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(2500)
            }
        }
    }

    private fun loadInitialData() {
        val pass = repository.getSavedPassword().ifBlank { "arafat@@##" }
        val range = repository.getSelectedRange()
        val key = repository.getApiKey()

        _uiState.value = _uiState.value.copy(
            customPassword = pass,
            selectedRange = range,
            apiKey = key
        )

        addLog("🚀 Tool Initialized (Python-parity mode: V1 Create & CAA Checker)")
        refreshFacebookRanges()
    }

    private fun observeAccounts() {
        viewModelScope.launch {
            repository.allAccounts.collectLatest { list ->
                _uiState.value = _uiState.value.copy(accountsHistory = list)
            }
        }
    }

    fun setCustomPassword(newPass: String) {
        val trimmed = newPass.trim()
        repository.setSavedPassword(trimmed)
        _uiState.value = _uiState.value.copy(customPassword = trimmed)
    }

    fun selectRange(range: String) {
        repository.setSelectedRange(range)
        _uiState.value = _uiState.value.copy(selectedRange = range)
        addLog("✓ Selected Range: $range")
    }

    fun refreshFacebookRanges() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRanges = true)
            addLog("[•] Fetching live ranges from panel...")
            val ranges = repository.getLiveFacebookRanges()
            val currentSelected = _uiState.value.selectedRange
            val hasSelected = ranges.any { it.code == currentSelected }
            val newSelected = if (hasSelected) currentSelected else (ranges.firstOrNull()?.code ?: "")

            repository.setSelectedRange(newSelected)

            val messagesMap = ranges.filter { it.message.isNotBlank() }
                .associate { it.code to it.message }

            _uiState.value = _uiState.value.copy(
                facebookRanges = ranges,
                selectedRange = newSelected,
                rangeMessages = messagesMap,
                isLoadingRanges = false
            )
            addLog("✓ Found ${ranges.size} live ranges")
        }
    }

    private val isCreatingInViewModel = java.util.concurrent.atomic.AtomicBoolean(false)

    fun createAccountNow(customPass: String? = null, context: Context? = null) {
        if (!isCreatingInViewModel.compareAndSet(false, true)) {
            _uiState.value = _uiState.value.copy(statusMessage = "⚠️ Account creation is already in progress...")
            return
        }

        val range = _uiState.value.selectedRange
        if (range.isBlank()) {
            isCreatingInViewModel.set(false)
            _uiState.value = _uiState.value.copy(
                isCreatingAccount = false,
                statusMessage = "❌ No range selected! Please select a range."
            )
            return
        }

        val passwordToUse = (customPass ?: _uiState.value.customPassword).ifBlank { "arafat@@##" }
        setCustomPassword(passwordToUse)

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isCreatingAccount = true,
                    statusMessage = "⏳ Fetching checked number for range $range..."
                )
                addLog("[•] Fetching checked number from panel for range $range...")

                val (phone, numStatus) = NetworkClient.acquireCheckedNumber(range)

                if (phone.isNullOrBlank()) {
                    addLog("✖ Failed to fetch a usable free number from panel.")
                    _uiState.value = _uiState.value.copy(
                        isCreatingAccount = false,
                        statusMessage = "❌ Failed to acquire usable number from range $range"
                    )
                    return@launch
                }

                addLog("✔ NUMBER: +$phone [$numStatus] — Creating account using V1...")
                _uiState.value = _uiState.value.copy(
                    statusMessage = "📱 +$phone [$numStatus] — Creating account using V1..."
                )

                val result = repository.createAccountForNumber(phone, range, passwordToUse)

                if (result.success && result.uid.isNotBlank()) {
                    addLog("✔ [SUCCESS] Account created successfully!")
                    addLog("   UID: ${result.uid} | PASS: $passwordToUse | PHONE: +$phone")
                    addLog("   NAME: ${result.name}")
                    addLog("[OTP MONITOR] Listening for OTPs on +$phone for 20 minutes...")

                    if (context != null) {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("NUMBER", phone)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Account Created! Number Copied: +$phone", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isCreatingAccount = false,
                        statusMessage = "✅ Account Created! UID: ${result.uid} | +$phone"
                    )
                } else {
                    val errMsg = result.error.ifEmpty { "Registration rejected" }
                    addLog("✖ [FAIL] Account creation failed: $errMsg")
                    _uiState.value = _uiState.value.copy(
                        isCreatingAccount = false,
                        statusMessage = "❌ Creation Failed: $errMsg"
                    )
                }
            } finally {
                isCreatingInViewModel.set(false)
            }
        }
    }

    fun setManualPhoneInput(phone: String) {
        _uiState.value = _uiState.value.copy(manualCheckPhone = phone)
    }

    fun checkManualPhoneNumber() {
        val input = _uiState.value.manualCheckPhone.trim()
        if (input.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingManualPhone = true, manualCheckResult = null)
            addLog("[•] Checking availability for $input via GraphQL CAA Search...")
            val (status, desc) = NetworkClient.checkNumberGraphql(input)
            val resultText = if (status == "free") {
                addLog("✔ $input is [FREE] ($desc)")
                "FREE: $desc"
            } else {
                addLog("✖ $input is [TAKEN] ($desc)")
                "TAKEN: $desc"
            }
            _uiState.value = _uiState.value.copy(
                isCheckingManualPhone = false,
                manualCheckResult = resultText
            )
        }
    }

    fun checkOtpsNow() {
        viewModelScope.launch {
            addLog("[•] Checking OTPs manually...")
            val otps = repository.checkAndProcessOtps()
            addLog(if (otps.isNotEmpty()) "✔ ${otps.size} OTP(s) received!" else "No new OTPs found.")
        }
    }

    fun clearTerminalLogs() {
        _uiState.value = _uiState.value.copy(terminalLogs = emptyList())
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            repository.deleteAccountById(id)
            addLog("Deleted account from history")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllAccounts()
            addLog("Cleared all account history")
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
