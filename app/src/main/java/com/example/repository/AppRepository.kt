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

    suspend fun getLiveFacebookRanges(): List<String> {
        return NetworkClient.getLiveFacebookRanges()
    }

    suspend fun fetchNumber(rangeCode: String): String? {
        return NetworkClient.fetchNumber(rangeCode)
    }

    suspend fun createAccountForNumber(phone: String, rangeCode: String): FbCreationResult {
        val password = getSavedPassword().ifEmpty { "FBPass@${(100..999).random()}" }
        val result = NetworkClient.createFacebookAccount(phone, password)
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
        for (otp in otps) {
            accountDao.updateOtpForPhone(otp.number, otp.otpCode)
        }
        return otps
    }

    suspend fun deleteAccountById(id: Long) {
        accountDao.deleteAccountById(id)
    }

    suspend fun clearAllAccounts() {
        accountDao.deleteAllAccounts()
    }
}
