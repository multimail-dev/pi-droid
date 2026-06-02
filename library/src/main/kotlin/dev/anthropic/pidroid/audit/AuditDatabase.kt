package dev.anthropic.pidroid.audit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the audit log.
 */
@Database(entities = [AuditEntry::class], version = 1, exportSchema = false)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao

    companion object {
        fun create(context: Context): AuditDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AuditDatabase::class.java,
                "pidroid_audit.db",
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }

        /** In-memory database for testing */
        fun createInMemory(context: Context): AuditDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AuditDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
