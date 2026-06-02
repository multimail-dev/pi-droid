package dev.anthropic.pidroid.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Room DAO for the task journal.
 */
@Dao
interface TaskJournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntry)

    @Update
    suspend fun update(task: TaskEntry)

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getById(taskId: String): TaskEntry?

    @Query("SELECT * FROM tasks WHERE status IN ('ACTIVE')")
    suspend fun getActiveTasks(): List<TaskEntry>

    @Query("SELECT * FROM tasks WHERE status IN ('INTERRUPTED', 'PARKED')")
    suspend fun getDashboardTasks(): List<TaskEntry>

    @Query("SELECT * FROM tasks WHERE status = 'INTERRUPTED'")
    suspend fun getInterruptedTasks(): List<TaskEntry>

    @Query("UPDATE tasks SET status = 'INTERRUPTED', updated_at = :now WHERE status = 'ACTIVE'")
    suspend fun markAllActiveAsInterrupted(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM tasks WHERE expires_at IS NOT NULL AND expires_at < :now AND status IN ('COMPLETED', 'FAILED', 'DISCARDED')")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM tasks ORDER BY updated_at DESC")
    suspend fun getAll(): List<TaskEntry>
}
