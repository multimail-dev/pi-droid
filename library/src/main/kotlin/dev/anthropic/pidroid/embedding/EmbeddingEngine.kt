package dev.anthropic.pidroid.embedding

/**
 * Interface for computing text embeddings.
 *
 * The default implementation uses ONNX Runtime with all-MiniLM-L6-v2.
 * Test implementations can return fixed-size random/deterministic vectors.
 */
interface EmbeddingEngine {
    /** Embedding dimension (384 for MiniLM) */
    val dimension: Int

    /** Compute embedding for a single text. Returns a float array of size [dimension]. */
    suspend fun embed(text: String): FloatArray

    /** Compute embeddings for a batch of texts. */
    suspend fun embedBatch(texts: List<String>): List<FloatArray>

    /** Release resources (ONNX session, etc.) */
    fun close()
}
