package com.neko7ina.wallet.assistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelDocumentDao {
    @Query("SELECT payload FROM travel_documents ORDER BY departureEpochMillis ASC")
    fun observeAllPayloads(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: StoredTravelDocument)
}
