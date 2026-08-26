package com.neko7ina.wallet.assistant.settings

import android.content.Context

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

object ReminderTimingConstraints {
    const val DEPARTURE_STEP_MINUTES = 15
    const val LIVE_STEP_MINUTES = 5
    const val DEPARTURE_MIN_MINUTES = 30
    const val DEPARTURE_MAX_MINUTES = 12 * 60
    const val DEPARTURE_DEFAULT_MINUTES = 3 * 60
    const val LIVE_MIN_MINUTES = 15
    const val LIVE_MAX_MINUTES = 60
    const val LIVE_DEFAULT_MINUTES = 30
}

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var newTripsReminderEnabled: Boolean
        get() = preferences.getBoolean(KEY_NEW_TRIPS_REMINDER, false)
        set(value) {
            preferences.edit().putBoolean(KEY_NEW_TRIPS_REMINDER, value).apply()
        }

    var ignoreDepartedTripsOnImport: Boolean
        get() = preferences.getBoolean(KEY_IGNORE_DEPARTED_TRIPS_ON_IMPORT, true)
        set(value) {
            preferences.edit().putBoolean(KEY_IGNORE_DEPARTED_TRIPS_ON_IMPORT, value).apply()
        }

    var autoArchiveDepartedTrips: Boolean
        get() = preferences.getBoolean(KEY_AUTO_ARCHIVE_DEPARTED_TRIPS, false)
        set(value) {
            preferences.edit().putBoolean(KEY_AUTO_ARCHIVE_DEPARTED_TRIPS, value).apply()
        }

    var departureReminderMinutes: Int
        get() = preferences.getInt(
            KEY_DEPARTURE_REMINDER_MINUTES,
            ReminderTimingConstraints.DEPARTURE_DEFAULT_MINUTES,
        ).coerceIn(
            ReminderTimingConstraints.DEPARTURE_MIN_MINUTES,
            ReminderTimingConstraints.DEPARTURE_MAX_MINUTES,
        )
        set(value) {
            preferences.edit().putInt(
                KEY_DEPARTURE_REMINDER_MINUTES,
                value.coerceIn(
                    ReminderTimingConstraints.DEPARTURE_MIN_MINUTES,
                    ReminderTimingConstraints.DEPARTURE_MAX_MINUTES,
                ),
            ).apply()
        }

    var liveStatusMinutes: Int
        get() = preferences.getInt(
            KEY_LIVE_STATUS_MINUTES,
            ReminderTimingConstraints.LIVE_DEFAULT_MINUTES,
        ).coerceIn(
            ReminderTimingConstraints.LIVE_MIN_MINUTES,
            ReminderTimingConstraints.LIVE_MAX_MINUTES,
        )
        set(value) {
            preferences.edit().putInt(
                KEY_LIVE_STATUS_MINUTES,
                value.coerceIn(
                    ReminderTimingConstraints.LIVE_MIN_MINUTES,
                    ReminderTimingConstraints.LIVE_MAX_MINUTES,
                ),
            ).apply()
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
        const val KEY_IGNORE_DEPARTED_TRIPS_ON_IMPORT = "ignore_departed_trips_on_import"
        const val KEY_AUTO_ARCHIVE_DEPARTED_TRIPS = "auto_archive_departed_trips"
        const val KEY_DEPARTURE_REMINDER_MINUTES = "departure_reminder_minutes"
        const val KEY_LIVE_STATUS_MINUTES = "live_status_minutes"
        const val KEY_GOOGLE_WALLET_VISIBLE = "google_wallet_visible"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
