package com.neko7ina.wallet.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StoredTravelDocument::class],
    version = 2,
    exportSchema = true,
)
abstract class TravelWalletDatabase : RoomDatabase() {
    abstract fun travelDocumentDao(): TravelDocumentDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE travel_documents " +
                        "ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        @Volatile
        private var instance: TravelWalletDatabase? = null

        fun getInstance(context: Context): TravelWalletDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TravelWalletDatabase::class.java,
                "travel-wallet.db",
            ).addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
