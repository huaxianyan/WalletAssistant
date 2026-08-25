package com.neko7ina.wallet.assistant.reminder

import android.content.Context

class ReminderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var newTripsEnabledByDefault: Boolean
        get() = preferences.getBoolean(KEY_NEW_TRIPS_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_NEW_TRIPS_ENABLED, value).apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "trip_reminders"
        const val KEY_NEW_TRIPS_ENABLED = "new_trips_enabled"
    }
}
