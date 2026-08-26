package com.neko7ina.wallet.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StoredTravelDocument::class],
    version = 4,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE travel_documents " +
                        "ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "DROP INDEX index_travel_documents_providerCode_reservationReference",
                )
                database.execSQL(
                    "CREATE INDEX index_travel_documents_providerCode_reservationReference " +
                        "ON travel_documents(providerCode, reservationReference)",
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
