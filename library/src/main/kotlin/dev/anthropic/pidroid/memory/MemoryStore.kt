package dev.anthropic.pidroid.memory

import dev.anthropic.pidroid.embedding.EmbeddingEngine
import java.util.UUID

/**
 * Semantic memory store backed by Room + vector search.
 *
 * Stores text content with embeddings and retrieves by semantic similarity.
 */
class MemoryStore(
    private val dao: MemoryDao,
    private val embeddingEngine: EmbeddingEngine,
) {

    /**
     * Store a piece of information in memory.
     * @return the generated memory ID
     */
    suspend fun store(content: String, metadataJson: String? = null): String {
        val id = "mem_${UUID.randomUUID().toString().take(12)}"
        val embedding = embeddingEngine.embed(content)
        val entry = MemoryEntry(
            id = id,
            content = content,
            embedding = VectorSearch.floatsToBytes(embedding),
            metadataJson = metadataJson,
        )
        dao.insert(entry)
        return id
    }

    /**
     * Search memory by semantic similarity.
     *
     * @param query The search query
     * @param limit Max results
     * @param minSimilarity Minimum cosine similarity threshold
     * @return List of matching memories with similarity scores
     */
    suspend fun search(
        query: String,
        limit: Int = 10,
        minSimilarity: Float = 0.0f,
    ): List<MemorySearchResult> {
        val queryEmbedding = embeddingEngine.embed(query)
        val candidates = dao.getAll()

        return VectorSearch.search(queryEmbedding, candidates, limit, minSimilarity)
            .map { (entry, similarity) ->
                MemorySearchResult(
                    id = entry.id,
                    content = entry.content,
                    similarity = similarity,
                    metadataJson = entry.metadataJson,
                )
            }
    }

    /**
     * Delete a memory entry by ID.
     * @return true if deleted, false if not found
     */
    suspend fun delete(memoryId: String): Boolean {
        return dao.deleteById(memoryId) > 0
    }

    /**
     * Get memory count.
     */
    suspend fun count(): Int = dao.count()
}

data class MemorySearchResult(
    val id: String,
    val content: String,
    val similarity: Float,
    val metadataJson: String? = null,
)
