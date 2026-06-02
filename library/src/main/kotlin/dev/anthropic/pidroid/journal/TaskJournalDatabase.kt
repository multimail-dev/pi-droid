package dev.anthropic.pidroid.journal

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Room database for the task journal.
 * Uses WAL mode for concurrent read/write safety.
 */
@Database(entities = [TaskEntry::class], version = 1, exportSchema = false)
@TypeConverters(TaskStatusConverter::class)
abstract class TaskJournalDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskJournalDao

    companion object {
        fun create(context: Context): TaskJournalDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TaskJournalDatabase::class.java,
                "pidroid_task_journal.db",
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }

        /** In-memory database for testing */
        fun createInMemory(context: Context): TaskJournalDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                TaskJournalDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}

class TaskStatusConverter {
    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)
}
