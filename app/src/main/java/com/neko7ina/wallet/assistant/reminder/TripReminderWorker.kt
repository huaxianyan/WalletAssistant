package com.neko7ina.wallet.assistant.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.neko7ina.wallet.assistant.MainActivity
import com.neko7ina.wallet.assistant.R
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.stableId
import java.time.Instant
import java.time.format.DateTimeFormatter

internal enum class ReminderKind {
    STANDARD,
    LIVE,
    END,
}

class TripReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
        val kind = inputData.getString(KEY_KIND)?.let(ReminderKind::valueOf) ?: return Result.failure()
        if (kind == ReminderKind.END) {
            cancelNotification(applicationContext, documentId)
            return Result.success()
        }
        if (!canPostNotifications(applicationContext)) return Result.success()

        createNotificationChannel(applicationContext)
        val serviceNumber = inputData.getString(KEY_SERVICE_NUMBER) ?: return Result.failure()
        val route = inputData.getString(KEY_ROUTE) ?: return Result.failure()
        val seat = inputData.getString(KEY_SEAT) ?: return Result.failure()
        val departure = inputData.getString(KEY_DEPARTURE) ?: return Result.failure()
        val displayEnd = inputData.getLong(KEY_DISPLAY_END, 0L)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_train_notification)
            .setContentTitle(
                if (kind == ReminderKind.LIVE) {
                    "$serviceNumber 即将发车"
                } else {
                    "$serviceNumber 乘车提醒"
                },
            )
            .setContentText("$departure，$route · $seat")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$departure，$route，$seat"))
            .setContentIntent(contentIntent(applicationContext, documentId))
            .setAutoCancel(kind == ReminderKind.STANDARD)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (kind == ReminderKind.LIVE) {
                    setOngoing(true)
                    setWhen(displayEnd)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    if (Build.VERSION.SDK_INT >= 36) {
                        setRequestPromotedOngoing(true)
                    }
                }
            }
            .build()
        NotificationManagerCompat.from(applicationContext).notify(notificationId(documentId), notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "trip_departure_reminders"
        private const val KEY_DOCUMENT_ID = "document_id"
        private const val KEY_KIND = "kind"
        private const val KEY_SERVICE_NUMBER = "service_number"
        private const val KEY_ROUTE = "route"
        private const val KEY_SEAT = "seat"
        private const val KEY_DEPARTURE = "departure"
        private const val KEY_DISPLAY_END = "display_end"
        private val DEPARTURE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")

        internal fun inputData(
            document: TravelDocument,
            kind: ReminderKind,
            displayEnd: Instant,
        ): Data {
            val segment = document.segments.first()
            val seat = segment.seatAssignments.firstOrNull()
            return workDataOf(
                KEY_DOCUMENT_ID to document.stableId(),
                KEY_KIND to kind.name,
                KEY_SERVICE_NUMBER to segment.serviceNumber,
                KEY_ROUTE to "${segment.origin.name} → ${segment.destination.name}",
                KEY_SEAT to seat?.let { "${it.section} 车 ${it.seat}" }.orEmpty(),
                KEY_DEPARTURE to segment.departureTime.format(DEPARTURE_FORMAT),
                KEY_DISPLAY_END to displayEnd.toEpochMilli(),
            )
        }

        fun cancelNotification(context: Context, documentId: String) {
            NotificationManagerCompat.from(context).cancel(notificationId(documentId))
        }

        private fun notificationId(documentId: String): Int = documentId.hashCode() and Int.MAX_VALUE

        private fun canPostNotifications(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "乘车提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "在行程即将开始时显示乘车信息"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun contentIntent(context: Context, documentId: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("document_id", documentId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                notificationId(documentId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
