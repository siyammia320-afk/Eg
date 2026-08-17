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

    companion object {
        private const val KEY_PASSWORD = "saved_password"
        private const val KEY_SELECTED_RANGE = "selected_range"
        private const val KEY_SERVICE_ACTIVE = "service_active"
    }
}
