package dev.anthropic.pidroid.memory

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MemoryStoreTest {
    private lateinit var db: MemoryDatabase
    private lateinit var dao: MemoryDao
    private lateinit var store: MemoryStore
    private lateinit var engine: FakeEmbeddingEngine

    @Before
    fun setup() {
        db = MemoryDatabase.createInMemory(RuntimeEnvironment.getApplication())
        dao = db.memoryDao()
        engine = FakeEmbeddingEngine()
        store = MemoryStore(dao, engine)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `store inserts entry with embedding`() = runTest {
        val id = store.store("User prefers dark mode")
        assertTrue(id.startsWith("mem_"))
        assertEquals(1, store.count())
    }

    @Test
    fun `search returns entries ranked by similarity`() = runTest {
        store.store("User prefers dark mode")
        store.store("User likes coffee at 9am")
        store.store("User's birthday is March 15")

        val results = store.search("dark mode preference")
        assertTrue(results.isNotEmpty())
        // The same text should have highest similarity to itself
    }

    @Test
    fun `search with same text returns highest similarity`() = runTest {
        store.store("The quick brown fox")

        val results = store.search("The quick brown fox")
        assertEquals(1, results.size)
        // Exact same text → same embedding → similarity = 1.0
        assertEquals(1.0f, results[0].similarity, 0.001f)
    }

    @Test
    fun `search with minSimilarity filters low matches`() = runTest {
        store.store("cats are great pets")
        store.store("dogs are loyal companions")
        store.store("quantum physics is fascinating")

        val results = store.search("cats are great pets", minSimilarity = 0.99f)
        // Only exact match should pass high threshold
        assertEquals(1, results.size)
    }

    @Test
    fun `delete removes entry`() = runTest {
        val id = store.store("Temporary memory")
        assertEquals(1, store.count())

        val deleted = store.delete(id)
        assertTrue(deleted)
        assertEquals(0, store.count())
    }

    @Test
    fun `delete nonexistent returns false`() = runTest {
        val deleted = store.delete("mem_nonexistent")
        assertFalse(deleted)
    }

    @Test
    fun `empty store search returns empty list`() = runTest {
        val results = store.search("anything")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `store with metadata preserves it`() = runTest {
        store.store("Important fact", """{"source":"user"}""")

        val results = store.search("Important fact")
        assertEquals(1, results.size)
        assertEquals("""{"source":"user"}""", results[0].metadataJson)
    }

    @Test
    fun `multiple stores with same content get different IDs`() = runTest {
        val id1 = store.store("Same content")
        val id2 = store.store("Same content")
        // IDs are UUID-based so always different
        assertTrue(id1 != id2)
        assertEquals(2, store.count())
    }

    @Test
    fun `search limit constrains results`() = runTest {
        repeat(5) { i ->
            store.store("Memory entry $i")
        }

        val results = store.search("Memory entry", limit = 3)
        assertEquals(3, results.size)
    }
}
