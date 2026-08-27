package com.neko7ina.wallet.assistant.email

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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neko7ina.wallet.assistant.MainActivity
import com.neko7ina.wallet.assistant.R
import com.neko7ina.wallet.assistant.settings.AppPreferences
import com.neko7ina.wallet.assistant.settings.AutomaticEmailSyncStatus
import java.util.concurrent.TimeUnit

class AutomaticEmailSyncScheduler(private val context: Context) {
    fun reconcile() {
        val preferences = AppPreferences(context)
        if (!preferences.automaticEmailSyncEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutomaticEmailSyncWorker>(
            preferences.automaticEmailSyncInterval.hours,
            TimeUnit.HOURS,
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        private const val WORK_NAME = "automatic_email_sync"
    }
}

class AutomaticEmailSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val preferences = AppPreferences(applicationContext)
        if (!preferences.automaticEmailSyncEnabled) return Result.success()
        return try {
            when (
                val outcome = EmailSyncCoordinator(applicationContext).sync(
                    requireExistingCheckpoint = true,
                )
            ) {
                EmailSyncOutcome.NoAccount,
                EmailSyncOutcome.InitialSyncRequired,
                -> {
                    updateStatus(preferences, AutomaticEmailSyncStatus.INITIAL_SYNC_REQUIRED)
                    preferences.automaticEmailSyncEnabled = false
                    AutomaticEmailSyncScheduler(applicationContext).reconcile()
                    Result.success()
                }

                EmailSyncOutcome.NoNewRailwayMessages,
                EmailSyncOutcome.NoRecognizableTrips,
                -> {
                    updateStatus(preferences, AutomaticEmailSyncStatus.SUCCESS)
                    Result.success()
                }

                is EmailSyncOutcome.PendingConfirmation -> {
                    updateStatus(preferences, AutomaticEmailSyncStatus.PENDING_CONFIRMATION)
                    EmailSyncNotification.show(applicationContext)
                    Result.success()
                }
            }
        } catch (_: ImapAuthenticationException) {
            updateStatus(preferences, AutomaticEmailSyncStatus.FAILED)
            preferences.automaticEmailSyncEnabled = false
            AutomaticEmailSyncScheduler(applicationContext).reconcile()
            Result.failure()
        } catch (_: Exception) {
            updateStatus(preferences, AutomaticEmailSyncStatus.FAILED)
            Result.retry()
        }
    }

    private fun updateStatus(
        preferences: AppPreferences,
        status: AutomaticEmailSyncStatus,
    ) {
        preferences.automaticEmailSyncStatus = status
        preferences.automaticEmailSyncStatusAtEpochMillis = System.currentTimeMillis()
    }
}

object EmailSyncNotification {
    const val EXTRA_OPEN_PENDING_EMAIL_IMPORT = "open_pending_email_import"
    private const val CHANNEL_ID = "email_sync_results"
    private const val NOTIFICATION_ID = 12306

    fun show(context: Context) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_PENDING_EMAIL_IMPORT, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_china_railway_notification)
            .setContentTitle("发现新的铁路行程")
            .setContentText("打开「出行」检查并保存")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "邮箱同步",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "发现需要确认的新行程时通知"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
