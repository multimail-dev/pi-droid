package dev.anthropic.pidroid.journal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a task in the journal.
 *
 * The task journal enables crash recovery: on crash, active tasks are found
 * and can be resumed from their last checkpoint.
 */
@Entity(tableName = "tasks")
data class TaskEntry(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "prompt")
    val prompt: String,

    @ColumnInfo(name = "status")
    val status: TaskStatus,

    @ColumnInfo(name = "step_index")
    val stepIndex: Int = 0,

    @ColumnInfo(name = "step_state_json")
    val stepStateJson: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
)
