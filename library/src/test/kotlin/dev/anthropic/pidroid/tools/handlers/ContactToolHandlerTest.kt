package dev.anthropic.pidroid.tools.handlers

import dev.anthropic.pidroid.android.ContactAccessor
import dev.anthropic.pidroid.tools.FakePermissionChecker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactToolHandlerTest {
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var accessor: ContactAccessor
    private lateinit var handler: ContactToolHandler
    private val context = FakeToolExecutionContext()

    @Before
    fun setup() {
        permissionChecker = FakePermissionChecker()
        permissionChecker.grantedPermissions.add("android.permission.READ_CONTACTS")
        accessor = ContactAccessor(RuntimeEnvironment.getApplication())
        handler = ContactToolHandler(accessor, permissionChecker)
    }

    @Test
    fun `search_contacts with query returns results`() = runTest {
        val args = buildJsonObject {
            put("query", "Alice")
            put("limit", 10)
        }

        val result = handler.execute("tc_1", args, context)

        // ContactsProvider not seeded in Robolectric, returns empty
        assertEquals(false, result.isError)
        assertEquals("[]", result.content)
    }

    @Test
    fun `search_contacts without permission returns error`() = runTest {
        permissionChecker.grantedPermissions.remove("android.permission.READ_CONTACTS")
        val args = buildJsonObject { put("query", "Alice") }

        val result = handler.execute("tc_2", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("READ_CONTACTS"))
    }

    @Test
    fun `get_contact_details with invalid ID returns error`() = runTest {
        val args = buildJsonObject { put("contact_id", "nonexistent_key") }

        val result = handler.execute("tc_3", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("not found"))
    }

    @Test
    fun `search with empty query returns error`() = runTest {
        val args = buildJsonObject { put("query", "   ") }

        val result = handler.execute("tc_4", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("empty"))
    }

    @Test
    fun `missing required field query returns error`() = runTest {
        val args = buildJsonObject { put("limit", 5) }

        val result = handler.execute("tc_5", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("query"))
    }

    @Test
    fun `get_contact_details without permission returns error`() = runTest {
        permissionChecker.grantedPermissions.remove("android.permission.READ_CONTACTS")
        val args = buildJsonObject { put("contact_id", "some_key") }

        val result = handler.execute("tc_6", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("READ_CONTACTS"))
    }
}
