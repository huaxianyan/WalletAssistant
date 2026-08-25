package com.neko7ina.wallet.assistant.archive

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TripAutoArchiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AUTO_ARCHIVE) return
        val documentId = intent.getStringExtra(KEY_DOCUMENT_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TripAutoArchiveScheduler(context).archiveIfDeparted(documentId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_AUTO_ARCHIVE =
            "com.neko7ina.wallet.assistant.action.AUTO_ARCHIVE_TRIP"
        private const val KEY_DOCUMENT_ID = "document_id"

        internal fun pendingIntent(
            context: Context,
            documentId: String,
            flags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ): PendingIntent? {
            val intent = Intent(context, TripAutoArchiveReceiver::class.java).apply {
                action = ACTION_AUTO_ARCHIVE
                data = Uri.parse("walletassistant://auto-archive/$documentId")
                putExtra(KEY_DOCUMENT_ID, documentId)
            }
            return PendingIntent.getBroadcast(
                context,
                documentId.hashCode(),
                intent,
                flags,
            )
        }
    }
}
