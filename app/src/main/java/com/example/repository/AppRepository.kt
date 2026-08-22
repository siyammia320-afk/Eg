package com.example.repository

import android.content.Context
import com.example.api.FbCreationResult
import com.example.api.NetworkClient
import com.example.api.OtpItem
import com.example.data.AccountDao
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.model.AccountEntity
import com.example.model.RangeItem
import kotlinx.coroutines.flow.Flow

class AppRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val accountDao: AccountDao = db.accountDao()
    val prefs: PreferencesManager = PreferencesManager(context)

    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    fun getSavedPassword(): String = prefs.savedPassword
    fun setSavedPassword(password: String) {
        prefs.savedPassword = password
    }

    fun getSelectedRange(): String = prefs.selectedRange
    fun setSelectedRange(range: String) {
        prefs.selectedRange = range
    }

    fun isServiceActive(): Boolean = prefs.isServiceActive
    fun setServiceActive(active: Boolean) {
        prefs.isServiceActive = active
    }

    fun getNameLanguage(): String = prefs.nameLanguage
    fun setNameLanguage(lang: String) {
        prefs.nameLanguage = lang
    }

    fun getGenderConfig(): String = prefs.genderConfig
    fun setGenderConfig(gender: String) {
        prefs.genderConfig = gender
    }

    fun getAgeFilter(): String = prefs.ageFilter
    fun setAgeFilter(age: String) {
        prefs.ageFilter = age
    }

    fun getTelegramChatId(): String = prefs.telegramChatId
    fun setTelegramChatId(chatId: String) {
        prefs.telegramChatId = chatId
    }

    fun getTelegramUsername(): String = prefs.telegramUsername
    fun setTelegramUsername(username: String) {
        prefs.telegramUsername = username
    }



    suspend fun sendTelegramOtp(number: String, otp: String, rawMessage: String = ""): Boolean {
        val chatId = getTelegramChatId()
        val username = getTelegramUsername()
        return NetworkClient.sendTelegramOtpForwarding(chatId, username, number, otp, rawMessage)
    }

    companion object {
        private val activeNumbers = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        private val processedOtps = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        private val otpLock = Any()
    }

    init {
        val storedKeys = prefs.getProcessedOtpKeys()
        if (storedKeys.isNotEmpty()) {
            processedOtps.addAll(storedKeys)
        }
    }

    fun registerActiveNumber(phone: String) {
        val clean = phone.replace(Regex("[^0-9]"), "")
        if (clean.isNotBlank()) {
            activeNumbers.add(clean)
        }
    }

    suspend fun getLiveFacebookRanges(): List<RangeItem> {
        return NetworkClient.getLiveFacebookRanges()
    }

    suspend fun fetchNumber(rangeCode: String): String? {
        val num = NetworkClient.fetchNumber(rangeCode)
        if (!num.isNullOrBlank()) {
            registerActiveNumber(num)
        }
        return num
    }

    suspend fun createAccountForNumber(phone: String, rangeCode: String): FbCreationResult {
        registerActiveNumber(phone)
        val password = NetworkClient.FIXED_PASSWORD
        val profile = com.example.data.NameGenerator.generateProfile(
            genderConfig = getGenderConfig(),
            langConfig = getNameLanguage(),
            ageConfig = getAgeFilter()
        )
        val result = NetworkClient.createFacebookAccount(phone, password, profile)
        // Strictly save to history ONLY if account creation was successful with a valid Facebook UID
        if (result.success && result.uid.isNotBlank() && result.uid != phone) {
            registerActiveNumber(result.phone)
            val entity = AccountEntity(
                phone = result.phone,
                uid = result.uid,
                cookie = result.cookie,
                password = result.password,
                name = result.name,
                rangeCode = rangeCode
            )
            accountDao.insertAccount(entity)
        }
        return result
    }

    suspend fun checkAndProcessOtps(): List<OtpItem> {
        val allAccounts = accountDao.getAllAccountsList()
        val userPhoneNumbers = mutableSetOf<String>()

        // 1. Collect all phone numbers from user's account history
        for (account in allAccounts) {
            val clean = account.phone.replace(Regex("[^0-9]"), "")
            if (clean.isNotBlank()) userPhoneNumbers.add(clean)
        }

        // 2. Collect active numbers requested in current session
        synchronized(activeNumbers) {
            userPhoneNumbers.addAll(activeNumbers)
        }

        // CRITICAL FIX: If user has NO active or created phone numbers, DO NOT process any OTPs!
        if (userPhoneNumbers.isEmpty()) {
            return emptyList()
        }

        val rawOtps = NetworkClient.checkOtps()
        if (rawOtps.isEmpty()) return emptyList()

        val matchedOtps = mutableListOf<OtpItem>()

        for (otp in rawOtps) {
            val cleanOtpPhone = otp.number.replace(Regex("[^0-9]"), "")
            if (cleanOtpPhone.isBlank()) continue

            // STRICT MATCH: Only process OTP if it matches one of the user's phone numbers
            val matchingUserPhone = userPhoneNumbers.find { isStrictPhoneMatch(it, cleanOtpPhone) }

            if (matchingUserPhone != null) {
                val uniqueKey = "${cleanOtpPhone}_${otp.otpCode}"
                var isNewOtp = false

                synchronized(otpLock) {
                    if (!processedOtps.contains(uniqueKey)) {
                        processedOtps.add(uniqueKey)
                        prefs.saveProcessedOtpKey(uniqueKey)
                        isNewOtp = true
                    }
                }

                if (isNewOtp) {
                    // Update account DB if account exists
                    for (account in allAccounts) {
                        val cleanAccPhone = account.phone.replace(Regex("[^0-9]"), "")
                        if (isStrictPhoneMatch(cleanAccPhone, cleanOtpPhone)) {
                            accountDao.updateOtpForPhone(account.phone, otp.otpCode)
                        }
                    }

                    // Auto forward to Telegram ONCE
                    sendTelegramOtp(otp.number, otp.otpCode, otp.rawMessage)

                    matchedOtps.add(otp)
                }
            }
        }

        return matchedOtps
    }

    private fun isStrictPhoneMatch(p1: String, p2: String): Boolean {
        val s1 = p1.replace(Regex("[^0-9]"), "")
        val s2 = p2.replace(Regex("[^0-9]"), "")
        if (s1.isEmpty() || s2.isEmpty()) return false

        // 1. Exact digit match
        if (s1 == s2) return true

        // 2. Exact suffix match with country code (e.g. 01712345678 vs 8801712345678)
        val minLen = minOf(s1.length, s2.length)
        if (minLen >= 9 && (s1.endsWith(s2) || s2.endsWith(s1))) {
            return true
        }

        return false
    }

    suspend fun deleteAccountById(id: Long) {
        accountDao.deleteAccountById(id)
    }

    suspend fun clearAllAccounts() {
        accountDao.deleteAllAccounts()
    }
}
