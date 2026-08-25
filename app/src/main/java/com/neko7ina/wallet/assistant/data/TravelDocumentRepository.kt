package com.neko7ina.wallet.assistant.data

import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.stableId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TravelDocumentRepository(
    private val dao: TravelDocumentDao,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun observeDocuments(): Flow<List<TravelDocument>> = dao.observeAllPayloads().map { payloads ->
        payloads.map(json::decodeFromString)
    }

    suspend fun save(document: TravelDocument) {
        val providerCode = document.provider.code
        val reservationReference = document.reservation.reference
        dao.upsert(
            StoredTravelDocument(
                id = document.stableId(),
                providerCode = providerCode,
                reservationReference = reservationReference,
                departureEpochMillis = document.segments.minOf { it.departureTime.toInstant().toEpochMilli() },
                payload = json.encodeToString(document),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

}
