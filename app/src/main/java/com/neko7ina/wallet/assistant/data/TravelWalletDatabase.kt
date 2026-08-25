package com.neko7ina.wallet.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StoredTravelDocument::class],
    version = 1,
    exportSchema = true,
)
abstract class TravelWalletDatabase : RoomDatabase() {
    abstract fun travelDocumentDao(): TravelDocumentDao

    companion object {
        @Volatile
        private var instance: TravelWalletDatabase? = null

        fun getInstance(context: Context): TravelWalletDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TravelWalletDatabase::class.java,
                "travel-wallet.db",
            ).build().also { instance = it }
        }
    }
}
