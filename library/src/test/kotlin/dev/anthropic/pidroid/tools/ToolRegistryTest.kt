package dev.anthropic.pidroid.tools

import dev.anthropic.pidroid.capabilities.CapabilityGrant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var registry: ToolRegistry

    @Before
    fun setup() {
        permissionChecker = FakePermissionChecker()
        registry = ToolRegistry(
            permissionChecker = permissionChecker,
            config = ToolRegistryConfig(),
        )
    }

    @Test
    fun `no capabilities declared produces empty tool set (fail-closed)`() = runTest {
        val snapshot = registry.activeTools.value
        assertEquals(0, snapshot.toolCount)
    }

    @Test
    fun `declare notification listener activates notification tools`() = runTest {
        permissionChecker.notificationListenerEnabled = true
        val snapshot = registry.declareCapability(
            CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER)
        )
        val toolNames = snapshot.tools.map { it.name }
        assertTrue("read_notifications" in toolNames)
        assertTrue("get_notification_channels" in toolNames)
    }

    @Test
    fun `declare READ_CONTACTS activates contact tools`() = runTest {
        permissionChecker.grantedPermissions.add("android.permission.READ_CONTACTS")
        val snapshot = registry.declareCapability(
            CapabilityGrant("android.permission.READ_CONTACTS")
        )
        val toolNames = snapshot.tools.map { it.name }
        assertTrue("search_contacts" in toolNames)
        assertTrue("get_contact_details" in toolNames)
    }

    @Test
    fun `revoke capability removes tools`() = runTest {
        permissionChecker.notificationListenerEnabled = true
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))

        val snapshot = registry.revokeCapability(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER)
        assertEquals(0, snapshot.toolCount)
    }

    @Test
    fun `permission not granted means tool not activated despite declaration`() = runTest {
        // Declare but don't grant the actual permission
        permissionChecker.grantedPermissions.clear() // READ_CONTACTS not granted
        val snapshot = registry.declareCapability(
            CapabilityGrant("android.permission.READ_CONTACTS")
        )
        val toolNames = snapshot.tools.map { it.name }
        assertTrue("search_contacts should not activate", "search_contacts" !in toolNames)
    }

    @Test
    fun `tool override disables tool`() = runTest {
        permissionChecker.notificationListenerEnabled = true
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))

        val snapshot = registry.registerToolOverride(
            "read_notifications",
            ToolOverride(enabled = false),
        )
        val toolNames = snapshot.tools.map { it.name }
        assertTrue("read_notifications should be disabled", "read_notifications" !in toolNames)
    }

    @Test
    fun `override cannot loosen confirmation for NON_LOOSABLE risk levels`() = runTest {
        // share_text has EXTERNAL_DRAFT risk which is NON_LOOSABLE
        permissionChecker.notificationListenerEnabled = true
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))

        try {
            registry.registerToolOverride(
                "share_text",
                ToolOverride(confirmationPolicy = ConfirmationPolicy.AUTOMATIC),
            )
            fail("Should throw ToolOverrideSecurityException")
        } catch (e: ToolOverrideSecurityException) {
            assertEquals("share_text", e.toolName)
            assertEquals(RiskLevel.EXTERNAL_DRAFT, e.riskLevel)
        }
    }

    @Test
    fun `deny-listed tools filtered from active set`() = runTest {
        val config = ToolRegistryConfig(denyList = setOf("get_battery_state"))
        val regWithDeny = ToolRegistry(permissionChecker, config)
        permissionChecker.notificationListenerEnabled = true

        val snapshot = regWithDeny.declareCapability(
            CapabilityGrant(CapabilityGrant.CAPABILITY_DEVICE_STATE)
        )
        val toolNames = snapshot.tools.map { it.name }
        assertTrue("get_battery_state" !in toolNames)
    }

    @Test
    fun `snapshot version increments on every recomputation`() = runTest {
        permissionChecker.notificationListenerEnabled = true
        val s1 = registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))
        val s2 = registry.refreshPermissionState()
        assertTrue(s2.version > s1.version)
    }

    @Test
    fun `extension tool respects maxToolCount cap`() = runTest {
        val config = ToolRegistryConfig(
            maxToolCount = 1,
            generatedToolPolicy = GeneratedToolPolicy.AUTO_APPROVE_READ_ONLY,
        )
        val reg = ToolRegistry(permissionChecker, config)
        permissionChecker.notificationListenerEnabled = true
        reg.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))

        try {
            reg.registerExtensionTool(
                ToolDefinition(
                    name = "ext_tool",
                    description = "test",
                    inputSchema = buildJsonObject { put("type", "object") },
                    category = ToolCategory.DEVICE,
                    riskLevel = RiskLevel.READ_ONLY,
                    isExtension = true,
                )
            )
            fail("Should throw ToolCapExceededException")
        } catch (e: ToolCapExceededException) {
            assertEquals("ext_tool", e.proposed)
        }
    }

    @Test
    fun `getToolByName returns tool or null`() = runTest {
        permissionChecker.notificationListenerEnabled = true
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))

        val tool = registry.getToolByName("read_notifications")
        assertTrue(tool != null)
        assertEquals("read_notifications", tool!!.name)

        assertNull(registry.getToolByName("nonexistent"))
    }
}
