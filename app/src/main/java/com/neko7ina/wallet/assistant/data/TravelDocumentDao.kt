package com.neko7ina.wallet.assistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelDocumentDao {
    @Query("SELECT * FROM travel_documents WHERE archived = 0 ORDER BY departureEpochMillis ASC")
    fun observeActive(): Flow<List<StoredTravelDocument>>

    @Query("SELECT * FROM travel_documents WHERE archived = 1 ORDER BY departureEpochMillis DESC")
    fun observeArchived(): Flow<List<StoredTravelDocument>>

    @Query("SELECT * FROM travel_documents WHERE id = :id")
    suspend fun findById(id: String): StoredTravelDocument?

    @Query("SELECT * FROM travel_documents WHERE archived = 0")
    suspend fun findActive(): List<StoredTravelDocument>

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

    @Query("DELETE FROM travel_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "UPDATE travel_documents SET archived = 1, reminderEnabled = 0 " +
            "WHERE id = :id AND archived = 0 AND departureEpochMillis <= :nowEpochMillis",
    )
    suspend fun archiveIfDeparted(id: String, nowEpochMillis: Long): Int

    @Query(
        "UPDATE travel_documents SET archived = :archived, " +
            "reminderEnabled = CASE WHEN :archived THEN 0 ELSE reminderEnabled END WHERE id = :id",
    )
    suspend fun setArchived(id: String, archived: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: StoredTravelDocument)
}
