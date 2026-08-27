package com.neko7ina.wallet.assistant.data

import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.stableId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SavedTravelDocument(
    val document: TravelDocument,
    val reminderEnabled: Boolean,
    val archived: Boolean,
)

class TravelDocumentRepository(
    private val dao: TravelDocumentDao,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun observeDocuments(): Flow<List<SavedTravelDocument>> = dao.observeActive().map { storedDocuments ->
        storedDocuments.map(::decode)
    }

    fun observeArchivedDocuments(): Flow<List<SavedTravelDocument>> =
        dao.observeArchived().map { storedDocuments -> storedDocuments.map(::decode) }

    fun observePendingEmailImport(): Flow<PendingEmailImport?> =
        dao.observePendingEmailImport().map { it?.let(::decodePendingEmailImport) }

    suspend fun replaceReservations(
        documents: List<TravelDocument>,
        defaultReminderEnabled: Boolean,
    ): List<SavedTravelDocument> = documents
        .groupBy { it.provider.code to it.reservation.reference }
        .flatMap { (reservationKey, incomingDocuments) ->
            val (providerCode, reservationReference) = reservationKey
            val existing = dao.findByReservation(providerCode, reservationReference)
            val existingById = existing.associateBy(StoredTravelDocument::id)
            val incomingById = incomingDocuments.associateBy { it.stableId() }
            val reminderMustMove = existing.any { stored ->
                stored.reminderEnabled &&
                    incomingById[stored.id]?.status != TravelDocumentStatus.CONFIRMED
            }
            val newConfirmedDocuments = incomingDocuments.filter { document ->
                document.status == TravelDocumentStatus.CONFIRMED &&
                    document.stableId() !in existingById
            }
            val reminderTransferTarget = newConfirmedDocuments
                .singleOrNull()
                ?.stableId()
                ?.takeIf { reminderMustMove }
            val now = System.currentTimeMillis()
            val storedDocuments = incomingDocuments.map { document ->
                val id = document.stableId()
                val previous = existingById[id]
                val confirmed = document.status == TravelDocumentStatus.CONFIRMED
                StoredTravelDocument(
                    id = id,
                    providerCode = providerCode,
                    reservationReference = reservationReference,
                    departureEpochMillis = document.segments.minOf {
                        it.departureTime.toInstant().toEpochMilli()
                    },
                    payload = json.encodeToString(document),
                    updatedAtEpochMillis = now,
                    reminderEnabled = confirmed && when {
                        previous != null -> previous.reminderEnabled
                        id == reminderTransferTarget -> true
                        else -> defaultReminderEnabled
                    },
                    archived = !confirmed || previous?.archived == true,
                )
            }
            dao.replaceReservation(providerCode, reservationReference, storedDocuments)
            storedDocuments.map(::decode)
        }

    suspend fun setReminderEnabled(id: String, enabled: Boolean): SavedTravelDocument? {
        dao.setReminderEnabled(id, enabled)
        return dao.findById(id)?.let(::decode)
    }

    suspend fun setArchived(id: String, archived: Boolean): SavedTravelDocument? {
        dao.setArchived(id, archived)
        return dao.findById(id)?.let(::decode)
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun allDocuments(): List<TravelDocument> =
        dao.findAll().map { decode(it).document }

    suspend fun getReminderEnabledDocuments(): List<TravelDocument> =
        dao.findReminderEnabled().map { decode(it).document }

    suspend fun getByReservations(documents: List<TravelDocument>): List<SavedTravelDocument> =
        documents
            .map { it.provider.code to it.reservation.reference }
            .distinct()
            .flatMap { (providerCode, reservationReference) ->
                dao.findByReservation(providerCode, reservationReference).map(::decode)
            }

    suspend fun getActiveDocuments(): List<SavedTravelDocument> = dao.findActive().map(::decode)

    suspend fun pendingEmailImport(): PendingEmailImport? =
        dao.findPendingEmailImport()?.let(::decodePendingEmailImport)

    suspend fun savePendingEmailImport(pending: PendingEmailImport) {
        dao.upsertPendingEmailImport(
            StoredPendingEmailImport(
                documentsPayload = json.encodeToString(pending.documents),
                warningsPayload = json.encodeToString(pending.warnings),
                checkpointPayload = json.encodeToString(pending.checkpoint),
                accountFingerprint = pending.accountFingerprint,
                ignoreDepartedTrips = pending.ignoreDepartedTrips,
                createdAtEpochMillis = pending.createdAtEpochMillis,
            ),
        )
    }

    suspend fun deletePendingEmailImport() {
        dao.deletePendingEmailImport()
    }

    suspend fun archiveIfDeparted(id: String, nowEpochMillis: Long): Boolean =
        dao.archiveIfDeparted(id, nowEpochMillis) > 0

    private fun decode(stored: StoredTravelDocument): SavedTravelDocument = SavedTravelDocument(
        document = json.decodeFromString(stored.payload),
        reminderEnabled = stored.reminderEnabled,
        archived = stored.archived,
    )

    private fun decodePendingEmailImport(stored: StoredPendingEmailImport): PendingEmailImport =
        PendingEmailImport(
            documents = json.decodeFromString(stored.documentsPayload),
            warnings = json.decodeFromString(stored.warningsPayload),
            checkpoint = json.decodeFromString(stored.checkpointPayload),
            accountFingerprint = stored.accountFingerprint,
            ignoreDepartedTrips = stored.ignoreDepartedTrips,
            createdAtEpochMillis = stored.createdAtEpochMillis,
        )
}
