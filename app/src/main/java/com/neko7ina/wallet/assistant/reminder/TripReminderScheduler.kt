package com.neko7ina.wallet.assistant.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.stableId
import java.time.Duration
import java.time.Instant

internal enum class ReminderKind {
    STANDARD,
    LIVE,
    END,
}

class TripReminderScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    fun canScheduleExactReminders(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun schedule(document: TravelDocument) {
        if (!canScheduleExactReminders()) return
        val departure = document.segments.minOf { it.departureTime.toInstant() }
        if (!departure.isAfter(Instant.now())) {
            cancel(document.stableId())
            return
        }

        val id = document.stableId()
        scheduleAlarm(id, STANDARD_SUFFIX, document, ReminderKind.STANDARD, departure.minus(STANDARD_LEAD))
        scheduleAlarm(id, LIVE_SUFFIX, document, ReminderKind.LIVE, departure.minus(LIVE_LEAD))
        scheduleAlarm(id, END_SUFFIX, document, ReminderKind.END, departure)
    }

    fun scheduleDebugSequence(document: TravelDocument) {
        check(canScheduleExactReminders()) { "Exact reminder permission is required" }
        val now = Instant.now()
        val id = document.stableId()
        scheduleAlarm(
            id,
            DEBUG_STANDARD_SUFFIX,
            document,
            ReminderKind.STANDARD,
            now.plusSeconds(120),
        )
        scheduleAlarm(
            id,
            DEBUG_LIVE_SUFFIX,
            document,
            ReminderKind.LIVE,
            now.plusSeconds(240),
            displayEnd = now.plusSeconds(420),
        )
        scheduleAlarm(
            id,
            DEBUG_END_SUFFIX,
            document,
            ReminderKind.END,
            now.plusSeconds(420),
        )
    }

    fun cancel(documentId: String) {
        ALL_SUFFIXES.forEach { suffix ->
            val pendingIntent = TripReminderReceiver.pendingIntent(
                context = applicationContext,
                documentId = documentId,
                suffix = suffix,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        TripReminderReceiver.cancelNotification(applicationContext, documentId)
    }

    private fun scheduleAlarm(
        id: String,
        suffix: String,
        document: TravelDocument,
        kind: ReminderKind,
        runAt: Instant,
        displayEnd: Instant = document.segments.minOf { it.departureTime.toInstant() },
    ) {
        val pendingIntent = TripReminderReceiver.pendingIntent(
            context = applicationContext,
            documentId = id,
            suffix = suffix,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            document = document,
            kind = kind,
            displayEnd = displayEnd,
        ) ?: error("Unable to create reminder PendingIntent")
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            runAt.toEpochMilli().coerceAtLeast(System.currentTimeMillis()),
            pendingIntent,
        )
    }

    private companion object {
        val STANDARD_LEAD: Duration = Duration.ofHours(3)
        val LIVE_LEAD: Duration = Duration.ofMinutes(30)

        const val STANDARD_SUFFIX = "standard"
        const val LIVE_SUFFIX = "live"
        const val END_SUFFIX = "end"
        const val DEBUG_STANDARD_SUFFIX = "debug-standard"
        const val DEBUG_LIVE_SUFFIX = "debug-live"
        const val DEBUG_END_SUFFIX = "debug-end"
        val ALL_SUFFIXES = listOf(
            STANDARD_SUFFIX,
            LIVE_SUFFIX,
            END_SUFFIX,
            DEBUG_STANDARD_SUFFIX,
            DEBUG_LIVE_SUFFIX,
            DEBUG_END_SUFFIX,
        )
    }
}
