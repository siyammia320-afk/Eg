package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.api.NetworkClient

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fb_creator_prefs", Context.MODE_PRIVATE)

    var savedPassword: String
        get() = NetworkClient.FIXED_PASSWORD
        set(value) { /* Fixed system password - locked */ }

    var selectedRange: String
        get() = prefs.getString(KEY_SELECTED_RANGE, "8801XXX") ?: "8801XXX"
        set(value) = prefs.edit().putString(KEY_SELECTED_RANGE, value).apply()

    var isServiceActive: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ACTIVE, value).apply()

    var nameLanguage: String
        get() = prefs.getString(KEY_NAME_LANGUAGE, "BANGLA") ?: "BANGLA"
        set(value) = prefs.edit().putString(KEY_NAME_LANGUAGE, value).apply()

    var genderConfig: String
        get() = prefs.getString(KEY_GENDER_CONFIG, "FEMALE") ?: "FEMALE"
        set(value) = prefs.edit().putString(KEY_GENDER_CONFIG, value).apply()

    var ageFilter: String
        get() = prefs.getString(KEY_AGE_FILTER, "18+") ?: "18+"
        set(value) = prefs.edit().putString(KEY_AGE_FILTER, value).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_CHAT_ID, value).apply()

    var telegramUsername: String
        get() = prefs.getString(KEY_TELEGRAM_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_USERNAME, value).apply()

    var floatingMode: String
        get() = prefs.getString(KEY_FLOATING_MODE, "FB") ?: "FB"
        set(value) = prefs.edit().putString(KEY_FLOATING_MODE, value).apply()

    var igSelectedCountry: String
        get() = prefs.getString(KEY_IG_COUNTRY, "BD") ?: "BD"
        set(value) = prefs.edit().putString(KEY_IG_COUNTRY, value).apply()

    fun getProcessedOtpKeys(): Set<String> {
        return prefs.getStringSet(KEY_PROCESSED_OTPS, emptySet()) ?: emptySet()
    }

    fun saveProcessedOtpKey(key: String) {
        val current = getProcessedOtpKeys().toMutableSet()
        current.add(key)
        if (current.size > 500) {
            val trimmed = current.toList().takeLast(400).toSet()
            prefs.edit().putStringSet(KEY_PROCESSED_OTPS, trimmed).apply()
        } else {
            prefs.edit().putStringSet(KEY_PROCESSED_OTPS, current).apply()
        }
    }

    companion object {
        private const val KEY_PASSWORD = "saved_password"
        private const val KEY_SELECTED_RANGE = "selected_range"
        private const val KEY_SERVICE_ACTIVE = "service_active"
        private const val KEY_NAME_LANGUAGE = "name_language"
        private const val KEY_GENDER_CONFIG = "gender_config"
        private const val KEY_AGE_FILTER = "age_filter"
        private const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        private const val KEY_TELEGRAM_USERNAME = "telegram_username"
        private const val KEY_FLOATING_MODE = "floating_mode"
        private const val KEY_IG_COUNTRY = "ig_country"
        private const val KEY_PROCESSED_OTPS = "processed_otps_set"
    }
}
