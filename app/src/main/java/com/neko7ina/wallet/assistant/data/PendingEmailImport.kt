package com.neko7ina.wallet.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.email.ImapSyncCheckpoint

@Entity(tableName = "pending_email_import")
data class StoredPendingEmailImport(
    @PrimaryKey val id: Int = SINGLE_PENDING_IMPORT_ID,
    val documentsPayload: String,
    val warningsPayload: String,
    val checkpointPayload: String,
    val accountFingerprint: String,
    val createdAtEpochMillis: Long,
)

data class PendingEmailImport(
    val documents: List<TravelDocument>,
    val warnings: List<String>,
    val checkpoint: ImapSyncCheckpoint,
    val accountFingerprint: String,
    val createdAtEpochMillis: Long,
)

const val SINGLE_PENDING_IMPORT_ID = 1
