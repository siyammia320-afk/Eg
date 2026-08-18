package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fb_creator_prefs", Context.MODE_PRIVATE)

    var savedPassword: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

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

    companion object {
        private const val KEY_PASSWORD = "saved_password"
        private const val KEY_SELECTED_RANGE = "selected_range"
        private const val KEY_SERVICE_ACTIVE = "service_active"
        private const val KEY_NAME_LANGUAGE = "name_language"
        private const val KEY_GENDER_CONFIG = "gender_config"
        private const val KEY_AGE_FILTER = "age_filter"
        private const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        private const val KEY_TELEGRAM_USERNAME = "telegram_username"
    }
}
