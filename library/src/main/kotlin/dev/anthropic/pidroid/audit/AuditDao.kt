package dev.anthropic.pidroid.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room DAO for the audit log.
 */
@Dao
interface AuditDao {

    @Insert
    suspend fun insert(entry: AuditEntry)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<AuditEntry>

    @Query("SELECT * FROM audit_log WHERE tool_name = :toolName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByTool(toolName: String, limit: Int = 50): List<AuditEntry>

    @Query("SELECT * FROM audit_log WHERE is_error = 1 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getErrors(limit: Int = 50): List<AuditEntry>

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun count(): Int

    @Query("DELETE FROM audit_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
