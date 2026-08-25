package com.neko7ina.wallet.assistant.data

import com.neko7ina.wallet.assistant.core.model.TravelDocument
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
                id = stableId(providerCode, reservationReference),
                providerCode = providerCode,
                reservationReference = reservationReference,
                departureEpochMillis = document.segments.minOf { it.departureTime.toInstant().toEpochMilli() },
                payload = json.encodeToString(document),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun stableId(providerCode: String, reservationReference: String): String {
        val source = "$providerCode\u0000$reservationReference"
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
