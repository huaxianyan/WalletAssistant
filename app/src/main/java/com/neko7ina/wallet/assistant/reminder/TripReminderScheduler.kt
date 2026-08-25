package com.neko7ina.wallet.assistant.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.stableId
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class TripReminderScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    fun schedule(document: TravelDocument) {
        val departure = document.segments.minOf { it.departureTime.toInstant() }
        if (!departure.isAfter(Instant.now())) {
            cancel(document.stableId())
            return
        }

        val id = document.stableId()
        enqueue(id, STANDARD_SUFFIX, document, ReminderKind.STANDARD, departure.minus(STANDARD_LEAD))
        enqueue(id, LIVE_SUFFIX, document, ReminderKind.LIVE, departure.minus(LIVE_LEAD))
        enqueue(id, END_SUFFIX, document, ReminderKind.END, departure)
    }

    fun scheduleDebugSequence(document: TravelDocument) {
        val now = Instant.now()
        val id = document.stableId()
        enqueue(id, DEBUG_STANDARD_SUFFIX, document, ReminderKind.STANDARD, now.plusSeconds(120))
        enqueue(
            id,
            DEBUG_LIVE_SUFFIX,
            document,
            ReminderKind.LIVE,
            now.plusSeconds(240),
            displayEnd = now.plusSeconds(420),
        )
        enqueue(id, DEBUG_END_SUFFIX, document, ReminderKind.END, now.plusSeconds(420))
    }

    fun cancel(documentId: String) {
        listOf(
            STANDARD_SUFFIX,
            LIVE_SUFFIX,
            END_SUFFIX,
            DEBUG_STANDARD_SUFFIX,
            DEBUG_LIVE_SUFFIX,
            DEBUG_END_SUFFIX,
        ).forEach { suffix ->
            workManager.cancelUniqueWork(workName(documentId, suffix))
        }
        TripReminderWorker.cancelNotification(applicationContext, documentId)
    }

    private fun enqueue(
        id: String,
        suffix: String,
        document: TravelDocument,
        kind: ReminderKind,
        runAt: Instant,
        displayEnd: Instant = document.segments.minOf { it.departureTime.toInstant() },
    ) {
        val request = OneTimeWorkRequestBuilder<TripReminderWorker>()
            .setInitialDelay(delayUntil(runAt), TimeUnit.MILLISECONDS)
            .setInputData(TripReminderWorker.inputData(document, kind, displayEnd))
            .build()
        workManager.enqueueUniqueWork(
            workName(id, suffix),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun delayUntil(runAt: Instant): Long = Duration.between(Instant.now(), runAt)
        .toMillis()
        .coerceAtLeast(0)

    private fun workName(documentId: String, suffix: String): String =
        "trip-reminder-$documentId-$suffix"

    private companion object {
        val STANDARD_LEAD: Duration = Duration.ofHours(3)
        val LIVE_LEAD: Duration = Duration.ofMinutes(30)

        const val STANDARD_SUFFIX = "standard"
        const val LIVE_SUFFIX = "live"
        const val END_SUFFIX = "end"
        const val DEBUG_STANDARD_SUFFIX = "debug-standard"
        const val DEBUG_LIVE_SUFFIX = "debug-live"
        const val DEBUG_END_SUFFIX = "debug-end"
    }
}
