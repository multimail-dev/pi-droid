package dev.anthropic.pidroid.audit

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an audit log entry.
 *
 * Stores metadata-only records of tool executions. Tool arguments are
 * NOT logged by default (privacy: no PII in audit trail).
 */
@Entity(tableName = "audit_log")
data class AuditEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "tool_name")
    val toolName: String? = null,

    @ColumnInfo(name = "risk_level")
    val riskLevel: String? = null,

    @ColumnInfo(name = "confirmation_result")
    val confirmationResult: String? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,

    @ColumnInfo(name = "is_error")
    val isError: Boolean = false,

    @ColumnInfo(name = "metadata_json")
    val metadataJson: String? = null,
)
