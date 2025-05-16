package com.example.expensetracker.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "expense_tracker_prefs", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_DARK_MODE = "dark_mode"
    }

    var isDarkMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_DARK_MODE, value).apply()
} 