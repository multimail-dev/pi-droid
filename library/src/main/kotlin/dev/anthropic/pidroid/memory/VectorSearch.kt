package dev.anthropic.pidroid.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Cosine similarity search over MemoryEntry embeddings.
 *
 * For MVP, all embeddings are loaded into memory for search. This is acceptable
 * for < 10K entries (10K × 1536 bytes = ~15MB). Post-MVP: consider sqlite-vec extension.
 */
object VectorSearch {

    /**
     * Search memories by cosine similarity to the query embedding.
     *
     * @param queryEmbedding The query vector
     * @param candidates All memory entries to search
     * @param limit Max results to return
     * @param minSimilarity Minimum cosine similarity threshold
     * @return Sorted list of (entry, similarity) pairs
     */
    fun search(
        queryEmbedding: FloatArray,
        candidates: List<MemoryEntry>,
        limit: Int = 10,
        minSimilarity: Float = 0.0f,
    ): List<Pair<MemoryEntry, Float>> {
        return candidates
            .map { entry ->
                val embedding = bytesToFloats(entry.embedding)
                val similarity = cosineSimilarity(queryEmbedding, embedding)
                entry to similarity
            }
            .filter { (_, similarity) -> similarity >= minSimilarity }
            .sortedByDescending { (_, similarity) -> similarity }
            .take(limit)
    }

    /**
     * Compute cosine similarity between two vectors.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimensions must match: ${a.size} vs ${b.size}" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }

    /**
     * Convert a FloatArray to bytes for storage.
     */
    fun floatsToBytes(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) buffer.putFloat(f)
        return buffer.array()
    }

    /**
     * Convert stored bytes back to a FloatArray.
     */
    fun bytesToFloats(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.getFloat() }
    }
}
