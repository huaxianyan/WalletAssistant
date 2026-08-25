package com.neko7ina.wallet.assistant.data

import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.stableId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SavedTravelDocument(
    val document: TravelDocument,
    val reminderEnabled: Boolean,
)

class TravelDocumentRepository(
    private val dao: TravelDocumentDao,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun observeDocuments(): Flow<List<SavedTravelDocument>> = dao.observeAll().map { storedDocuments ->
        storedDocuments.map(::decode)
    }

    suspend fun save(
        document: TravelDocument,
        defaultReminderEnabled: Boolean,
    ): SavedTravelDocument {
        val providerCode = document.provider.code
        val reservationReference = document.reservation.reference
        val reminderEnabled = dao.findByReservation(
            providerCode = providerCode,
            reservationReference = reservationReference,
        )?.reminderEnabled ?: defaultReminderEnabled
        val stored = StoredTravelDocument(
            id = document.stableId(),
            providerCode = providerCode,
            reservationReference = reservationReference,
            departureEpochMillis = document.segments.minOf {
                it.departureTime.toInstant().toEpochMilli()
            },
            payload = json.encodeToString(document),
            updatedAtEpochMillis = System.currentTimeMillis(),
            reminderEnabled = reminderEnabled,
        )
        dao.upsert(stored)
        return decode(stored)
    }

    suspend fun setReminderEnabled(id: String, enabled: Boolean): SavedTravelDocument? {
        dao.setReminderEnabled(id, enabled)
        return dao.findById(id)?.let(::decode)
    }

    private fun decode(stored: StoredTravelDocument): SavedTravelDocument = SavedTravelDocument(
        document = json.decodeFromString(stored.payload),
        reminderEnabled = stored.reminderEnabled,
    )
}
