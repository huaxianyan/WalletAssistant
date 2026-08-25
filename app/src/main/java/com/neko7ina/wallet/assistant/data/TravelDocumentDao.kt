package com.neko7ina.wallet.assistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelDocumentDao {
    @Query("SELECT * FROM travel_documents ORDER BY departureEpochMillis ASC")
    fun observeAll(): Flow<List<StoredTravelDocument>>

    @Query("SELECT * FROM travel_documents WHERE id = :id")
    suspend fun findById(id: String): StoredTravelDocument?

    @Query("SELECT * FROM travel_documents WHERE reminderEnabled = 1")
    suspend fun findReminderEnabled(): List<StoredTravelDocument>

    @Query(
        "SELECT * FROM travel_documents " +
            "WHERE providerCode = :providerCode AND reservationReference = :reservationReference",
    )
    suspend fun findByReservation(
        providerCode: String,
        reservationReference: String,
    ): StoredTravelDocument?

    @Query("UPDATE travel_documents SET reminderEnabled = :enabled WHERE id = :id")
    suspend fun setReminderEnabled(id: String, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: StoredTravelDocument)
}
