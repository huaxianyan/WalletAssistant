package com.neko7ina.wallet.assistant.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "travel_documents",
    indices = [
        Index(
            value = ["providerCode", "reservationReference"],
            unique = true,
        ),
    ],
)
data class StoredTravelDocument(
    @PrimaryKey val id: String,
    val providerCode: String,
    val reservationReference: String,
    val departureEpochMillis: Long,
    val payload: String,
    val updatedAtEpochMillis: Long,
)
