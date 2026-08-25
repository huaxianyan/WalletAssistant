package com.neko7ina.wallet.assistant.settings

import android.content.Context

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var newTripsReminderEnabled: Boolean
        get() = preferences.getBoolean(KEY_NEW_TRIPS_REMINDER, false)
        set(value) {
            preferences.edit().putBoolean(KEY_NEW_TRIPS_REMINDER, value).apply()
        }

    var googleWalletActionVisible: Boolean
        get() = preferences.getBoolean(KEY_GOOGLE_WALLET_VISIBLE, true)
        set(value) {
            preferences.edit().putBoolean(KEY_GOOGLE_WALLET_VISIBLE, value).apply()
        }

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(
                preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name,
            )
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            preferences.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "trip_reminders"
        const val KEY_NEW_TRIPS_REMINDER = "new_trips_enabled"
        const val KEY_GOOGLE_WALLET_VISIBLE = "google_wallet_visible"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
