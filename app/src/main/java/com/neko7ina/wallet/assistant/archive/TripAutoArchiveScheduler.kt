package com.neko7ina.wallet.assistant.archive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.neko7ina.wallet.assistant.core.model.stableId
import com.neko7ina.wallet.assistant.data.SavedTravelDocument
import com.neko7ina.wallet.assistant.data.TravelDocumentRepository
import com.neko7ina.wallet.assistant.data.TravelWalletDatabase
import com.neko7ina.wallet.assistant.hasDeparted
import com.neko7ina.wallet.assistant.reminder.TripReminderScheduler
import com.neko7ina.wallet.assistant.settings.AppPreferences
import java.time.Instant

class TripAutoArchiveScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)
    private val preferences = AppPreferences(applicationContext)
    private val repository = TravelDocumentRepository(
        TravelWalletDatabase.getInstance(applicationContext).travelDocumentDao(),
    )
    private val reminderScheduler = TripReminderScheduler(applicationContext)

    suspend fun reconcile() {
        repository.getActiveDocuments().forEach { saved ->
            scheduleOrArchive(saved)
        }
    }

    suspend fun scheduleOrArchive(saved: SavedTravelDocument) {
        val documentId = saved.document.stableId()
        if (!preferences.autoArchiveDepartedTrips) {
            cancel(documentId)
            return
        }
        if (saved.document.hasDeparted()) {
            cancel(documentId)
            return
        }
        val departure = saved.document.segments.minOf { it.departureTime.toInstant() }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            departure.toEpochMilli(),
            requireNotNull(TripAutoArchiveReceiver.pendingIntent(applicationContext, documentId)),
        )
    }

    suspend fun archiveIfDeparted(documentId: String) {
        if (!preferences.autoArchiveDepartedTrips) return
        if (repository.archiveIfDeparted(documentId, Instant.now().toEpochMilli())) {
            reminderScheduler.cancel(documentId)
        }
        cancel(documentId)
    }

    fun cancel(documentId: String) {
        val pendingIntent = TripAutoArchiveReceiver.pendingIntent(
            context = applicationContext,
            documentId = documentId,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
