package dev.anthropic.pidroid.memory

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MemoryEntry::class], version = 1, exportSchema = false)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        fun create(context: Context): MemoryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MemoryDatabase::class.java,
                "pidroid_memory.db",
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }

        fun createInMemory(context: Context): MemoryDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                MemoryDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MemoryEntry)

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntry?

    @Query("SELECT * FROM memories")
    suspend fun getAll(): List<MemoryEntry>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int
}
