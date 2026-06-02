package dev.anthropic.pidroid.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorSearchTest {

    @Test
    fun `cosine similarity of identical vectors is 1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(1.0f, VectorSearch.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `cosine similarity of orthogonal vectors is 0`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        assertEquals(0.0f, VectorSearch.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `cosine similarity of opposite vectors is -1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        assertEquals(-1.0f, VectorSearch.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `floats to bytes and back is identity`() {
        val original = floatArrayOf(1.0f, -0.5f, 3.14f, 0.0f)
        val bytes = VectorSearch.floatsToBytes(original)
        val restored = VectorSearch.bytesToFloats(bytes)

        assertEquals(original.size, restored.size)
        for (i in original.indices) {
            assertEquals(original[i], restored[i], 0.0001f)
        }
    }

    @Test
    fun `search returns results sorted by similarity`() {
        val query = floatArrayOf(1f, 0f, 0f)
        val entries = listOf(
            createEntry("id_far", floatArrayOf(0f, 1f, 0f)),    // orthogonal = 0
            createEntry("id_close", floatArrayOf(0.9f, 0.1f, 0f)), // close
            createEntry("id_exact", floatArrayOf(1f, 0f, 0f)),  // identical = 1
        )

        val results = VectorSearch.search(query, entries, limit = 10)

        assertEquals(3, results.size)
        assertEquals("id_exact", results[0].first.id)
        assertEquals("id_close", results[1].first.id)
        assertEquals("id_far", results[2].first.id)
    }

    @Test
    fun `search with minSimilarity filters results`() {
        val query = floatArrayOf(1f, 0f, 0f)
        val entries = listOf(
            createEntry("id_orth", floatArrayOf(0f, 1f, 0f)),   // sim = 0
            createEntry("id_same", floatArrayOf(1f, 0f, 0f)),   // sim = 1
        )

        val results = VectorSearch.search(query, entries, limit = 10, minSimilarity = 0.5f)
        assertEquals(1, results.size)
        assertEquals("id_same", results[0].first.id)
    }

    @Test
    fun `search with limit constrains output`() {
        val query = floatArrayOf(1f, 0f, 0f)
        val entries = List(10) { i ->
            createEntry("id_$i", floatArrayOf(1f - i * 0.05f, i * 0.05f, 0f))
        }

        val results = VectorSearch.search(query, entries, limit = 3)
        assertEquals(3, results.size)
    }

    @Test
    fun `zero vector returns 0 similarity`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertEquals(0.0f, VectorSearch.cosineSimilarity(a, b), 0.001f)
    }

    private fun createEntry(id: String, embedding: FloatArray): MemoryEntry {
        return MemoryEntry(
            id = id,
            content = "test content for $id",
            embedding = VectorSearch.floatsToBytes(embedding),
        )
    }
}
