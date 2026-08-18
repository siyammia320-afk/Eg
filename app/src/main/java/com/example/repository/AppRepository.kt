package com.example.repository

import android.content.Context
import com.example.api.FbCreationResult
import com.example.api.NetworkClient
import com.example.api.OtpItem
import com.example.data.AccountDao
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.model.AccountEntity
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

    suspend fun sendTelegramOtp(number: String, otp: String): Boolean {
        val chatId = getTelegramChatId()
        if (chatId.isBlank()) return false
        return NetworkClient.sendTelegramOtp(chatId, number, otp)
    }

    suspend fun getLiveFacebookRanges(): List<String> {
        return NetworkClient.getLiveFacebookRanges()
    }

    suspend fun fetchNumber(rangeCode: String): String? {
        return NetworkClient.fetchNumber(rangeCode)
    }

    suspend fun createAccountForNumber(phone: String, rangeCode: String): FbCreationResult {
        val password = getSavedPassword().ifEmpty { "FBPass@${(100..999).random()}" }
        val profile = com.example.data.NameGenerator.generateProfile(
            genderConfig = getGenderConfig(),
            langConfig = getNameLanguage(),
            ageConfig = getAgeFilter()
        )
        val result = NetworkClient.createFacebookAccount(phone, password, profile)
        // Strictly save to history ONLY if account creation was successful with a valid Facebook UID
        if (result.success && result.uid.isNotBlank() && result.uid != phone) {
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
        val otps = NetworkClient.checkOtps()
        if (otps.isNotEmpty()) {
            val allAccounts = accountDao.getAllAccountsList()
            for (otp in otps) {
                val cleanOtpPhone = otp.number.replace(Regex("[^0-9]"), "")
                if (cleanOtpPhone.isEmpty()) continue

                for (account in allAccounts) {
                    val cleanAccountPhone = account.phone.replace(Regex("[^0-9]"), "")
                    
                    // Comprehensive phone number matching for all country formats
                    val isMatch = isPhoneMatch(cleanAccountPhone, cleanOtpPhone)

                    if (isMatch) {
                        accountDao.updateOtpForPhone(account.phone, otp.otpCode)
                    }
                }
            }
        }
        return otps
    }

    private fun isPhoneMatch(p1: String, p2: String): Boolean {
        val s1 = p1.replace(Regex("[^0-9]"), "")
        val s2 = p2.replace(Regex("[^0-9]"), "")
        if (s1.isEmpty() || s2.isEmpty()) return false

        // 1. Direct exact digit match
        if (s1 == s2) return true

        // 2. Universal prefix/suffix match: e.g., one number contains the local digits of the other
        if (s1.endsWith(s2) || s2.endsWith(s1)) return true

        // 3. Substring match for numbers with at least 5 digits
        if (s1.length >= 5 && s2.length >= 5) {
            if (s1.contains(s2) || s2.contains(s1)) return true
        }

        // 4. Match last N digits (from 12 down to 5 digits)
        val minLen = minOf(s1.length, s2.length)
        if (minLen >= 5) {
            val checkLength = minOf(minLen, 12)
            for (k in checkLength downTo 5) {
                if (s1.takeLast(k) == s2.takeLast(k)) return true
            }
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
