package com.neko7ina.wallet.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StoredTravelDocument::class, StoredPendingEmailImport::class],
    version = 6,
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_email_import (" +
                        "id INTEGER NOT NULL, " +
                        "documentsPayload TEXT NOT NULL, " +
                        "warningsPayload TEXT NOT NULL, " +
                        "checkpointPayload TEXT NOT NULL, " +
                        "accountFingerprint TEXT NOT NULL, " +
                        "ignoreDepartedTrips INTEGER NOT NULL, " +
                        "createdAtEpochMillis INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE pending_email_import_new (" +
                        "id INTEGER NOT NULL, " +
                        "documentsPayload TEXT NOT NULL, " +
                        "warningsPayload TEXT NOT NULL, " +
                        "checkpointPayload TEXT NOT NULL, " +
                        "accountFingerprint TEXT NOT NULL, " +
                        "createdAtEpochMillis INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))",
                )
                database.execSQL(
                    "INSERT INTO pending_email_import_new (" +
                        "id, documentsPayload, warningsPayload, checkpointPayload, " +
                        "accountFingerprint, createdAtEpochMillis) " +
                        "SELECT id, documentsPayload, warningsPayload, checkpointPayload, " +
                        "accountFingerprint, createdAtEpochMillis FROM pending_email_import",
                )
                database.execSQL("DROP TABLE pending_email_import")
                database.execSQL(
                    "ALTER TABLE pending_email_import_new RENAME TO pending_email_import",
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
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )
                .build()
                .also { instance = it }
        }
    }
}
