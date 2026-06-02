package dev.anthropic.pidroid.memory

import dev.anthropic.pidroid.embedding.EmbeddingEngine
import kotlin.math.abs

/**
 * Fake embedding engine for testing.
 *
 * Produces deterministic embeddings based on text content:
 * - Uses a simple hash-based approach to generate different vectors for different texts
 * - Similar texts get somewhat similar vectors (not perfectly, but enough for testing)
 */
class FakeEmbeddingEngine(override val dimension: Int = 384) : EmbeddingEngine {

    override suspend fun embed(text: String): FloatArray {
        return generateDeterministicEmbedding(text)
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        return texts.map { generateDeterministicEmbedding(it) }
    }

    override fun close() { /* no-op */ }

    private fun generateDeterministicEmbedding(text: String): FloatArray {
        // Use text hash as seed for deterministic pseudo-random vector
        val seed = text.hashCode().toLong()
        val embedding = FloatArray(dimension)
        var state = seed
        for (i in 0 until dimension) {
            state = state * 6364136223846793005L + 1442695040888963407L
            embedding[i] = ((state ushr 33).toFloat() / Int.MAX_VALUE.toFloat()) - 0.5f
        }
        // Normalize to unit vector
        var norm = 0f
        for (v in embedding) norm += v * v
        norm = kotlin.math.sqrt(norm)
        if (norm > 0f) {
            for (i in embedding.indices) embedding[i] /= norm
        }
        return embedding
    }
}
