package dev.anthropic.pidroid.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a memory entry with embedding.
 *
 * The embedding is stored as a BLOB (384 floats × 4 bytes = 1536 bytes).
 * Vector search loads candidates into Kotlin for cosine similarity ranking.
 */
@Entity(tableName = "memories")
data class MemoryEntry(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray,

    @ColumnInfo(name = "metadata_json")
    val metadataJson: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEntry) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
