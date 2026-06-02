package dev.anthropic.pidroid.tools

import dev.anthropic.pidroid.capabilities.CapabilityGrant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages the dynamic tool registry for the agent.
 *
 * Computes the active tool set by intersecting:
 * 1. Canonical tool catalog
 * 2. Host-declared capability grants
 * 3. Android runtime permission state
 *
 * Adapted from misadventure: removed all bridge/sidecar push logic.
 * The registry feeds the agent loop directly via StateFlow.
 *
 * ## Thread Safety
 * All public methods are thread-safe. Internal state protected by Mutex.
 * Observation via [activeTools] StateFlow is inherently thread-safe.
 */
class ToolRegistry internal constructor(
    private val permissionChecker: PermissionChecker,
    private val config: ToolRegistryConfig = ToolRegistryConfig(),
    private val catalog: ToolCatalog = ToolCatalog(),
) {
    private val _activeTools = MutableStateFlow(ToolRegistrySnapshot.EMPTY)
    val activeTools: StateFlow<ToolRegistrySnapshot> = _activeTools.asStateFlow()

    private val mutex = Mutex()
    private val declaredCapabilities = mutableMapOf<String, CapabilityGrant>()
    private val toolOverrides = mutableMapOf<String, ToolOverride>()
    private val extensionTools = mutableMapOf<String, ToolDefinition>()
    private var lastPushVersion: Long = 0L

    // ─── Capability Management ─────────────────────────────────────────

    suspend fun declareCapability(grant: CapabilityGrant): ToolRegistrySnapshot {
        return mutex.withLock {
            declaredCapabilities[grant.capabilityId] = grant
            recomputeAndEmit()
        }
    }

    suspend fun declareCapabilities(grants: List<CapabilityGrant>): ToolRegistrySnapshot {
        return mutex.withLock {
            grants.forEach { declaredCapabilities[it.capabilityId] = it }
            recomputeAndEmit()
        }
    }

    suspend fun revokeCapability(capabilityId: String): ToolRegistrySnapshot {
        return mutex.withLock {
            declaredCapabilities.remove(capabilityId)
            recomputeAndEmit()
        }
    }

    suspend fun refreshPermissionState(): ToolRegistrySnapshot {
        return mutex.withLock {
            recomputeAndEmit()
        }
    }

    // ─── Tool Overrides ────────────────────────────────────────────────

    suspend fun registerToolOverride(
        toolName: String,
        override: ToolOverride,
    ): ToolRegistrySnapshot {
        return mutex.withLock {
            validateOverrideSecurity(toolName, override)
            toolOverrides[toolName] = override
            recomputeAndEmit()
        }
    }

    // ─── Extension Tools ───────────────────────────────────────────────

    suspend fun registerExtensionTool(tool: ToolDefinition): ToolRegistrySnapshot {
        require(tool.isExtension) { "registerExtensionTool requires isExtension=true" }
        return mutex.withLock {
            validateExtensionPolicy(tool)
            val totalCount = _activeTools.value.toolCount + 1
            if (totalCount > config.maxToolCount) {
                throw ToolCapExceededException(
                    current = _activeTools.value.toolCount,
                    max = config.maxToolCount,
                    proposed = tool.name,
                )
            }
            extensionTools[tool.name] = tool
            recomputeAndEmit()
        }
    }

    suspend fun removeExtensionTool(toolName: String): ToolRegistrySnapshot {
        return mutex.withLock {
            extensionTools.remove(toolName)
            recomputeAndEmit()
        }
    }

    // ─── Query ─────────────────────────────────────────────────────────

    fun getToolByName(name: String): ToolDefinition? {
        return _activeTools.value.tools.find { it.name == name }
    }

    fun getToolsByCategory(category: ToolCategory): List<ToolDefinition> {
        return _activeTools.value.tools.filter { it.category == category }
    }

    fun currentVersion(): Long = _activeTools.value.version

    // ─── Internal ──────────────────────────────────────────────────────

    private fun recomputeAndEmit(): ToolRegistrySnapshot {
        if (declaredCapabilities.isEmpty()) {
            val empty = ToolRegistrySnapshot(
                tools = emptyList(),
                version = ++lastPushVersion,
                computedAt = System.currentTimeMillis(),
                declaredCapabilityCount = 0,
                grantedCapabilityCount = 0,
            )
            _activeTools.value = empty
            return empty
        }

        val candidates = mutableListOf<ToolDefinition>()

        // Filter catalog tools
        for (tool in catalog.allTools()) {
            if (isToolEligible(tool)) {
                candidates.add(applyOverrides(tool))
            }
        }

        // Extension tools
        for ((_, extTool) in extensionTools) {
            if (isExtensionToolEligible(extTool)) {
                candidates.add(extTool)
            }
        }

        // Deny list
        candidates.removeAll { it.name in config.denyList }

        // Sort deterministically
        candidates.sortWith(compareBy({ it.category.ordinal }, { it.name }))

        val grantedCount = declaredCapabilities.count { (_, grant) ->
            isCapabilityGranted(grant)
        }

        val snapshot = ToolRegistrySnapshot(
            tools = candidates.toList(),
            version = ++lastPushVersion,
            computedAt = System.currentTimeMillis(),
            declaredCapabilityCount = declaredCapabilities.size,
            grantedCapabilityCount = grantedCount,
        )
        _activeTools.value = snapshot
        return snapshot
    }

    private fun isToolEligible(tool: ToolDefinition): Boolean {
        val override = toolOverrides[tool.name]
        if (override?.enabled == false) return false

        if (tool.requiredPermissions.isEmpty() && tool.requiredCapabilities.isEmpty()) {
            return declaredCapabilities.isNotEmpty()
        }

        for (permission in tool.requiredPermissions) {
            if (permission !in declaredCapabilities) return false
            if (!permissionChecker.isPermissionGranted(permission)) return false
        }

        for (capId in tool.requiredCapabilities) {
            val grant = declaredCapabilities[capId] ?: return false
            if (!isCapabilityGranted(grant)) return false
        }

        return true
    }

    private fun isCapabilityGranted(grant: CapabilityGrant): Boolean {
        return when {
            !grant.capabilityId.startsWith("pidroid://") ->
                permissionChecker.isPermissionGranted(grant.capabilityId)
            grant.capabilityId == CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER ->
                permissionChecker.isNotificationListenerEnabled()
            grant.capabilityId == CapabilityGrant.CAPABILITY_USAGE_STATS ->
                permissionChecker.isUsageStatsAccessGranted()
            else -> grant.granted
        }
    }

    private fun applyOverrides(tool: ToolDefinition): ToolDefinition {
        val override = toolOverrides[tool.name] ?: return tool
        return tool.copy(
            description = override.description ?: tool.description,
            defaultConfirmationPolicy = override.confirmationPolicy
                ?: tool.defaultConfirmationPolicy,
            timeoutMs = override.timeoutMs ?: tool.timeoutMs,
        )
    }

    private fun isExtensionToolEligible(tool: ToolDefinition): Boolean {
        if (!isToolEligible(tool)) return false
        return when (config.generatedToolPolicy) {
            GeneratedToolPolicy.DISABLED -> false
            GeneratedToolPolicy.REQUIRE_APPROVAL -> tool.name in config.approvedExtensions
            GeneratedToolPolicy.AUTO_APPROVE_READ_ONLY ->
                tool.riskLevel == RiskLevel.READ_ONLY || tool.name in config.approvedExtensions
        }
    }

    private fun validateOverrideSecurity(toolName: String, override: ToolOverride) {
        val tool = catalog.getByName(toolName)
            ?: extensionTools[toolName]
            ?: return

        if (override.confirmationPolicy != null &&
            tool.riskLevel in ConfirmationPolicy.NON_LOOSABLE_RISK_LEVELS
        ) {
            val currentPolicy = tool.defaultConfirmationPolicy
                ?: ConfirmationPolicy.defaultForRiskLevel(tool.riskLevel)
            if (override.confirmationPolicy.ordinal < currentPolicy.ordinal) {
                throw ToolOverrideSecurityException(
                    toolName = toolName,
                    riskLevel = tool.riskLevel,
                    currentPolicy = currentPolicy,
                    attemptedPolicy = override.confirmationPolicy,
                )
            }
        }
    }

    private fun validateExtensionPolicy(tool: ToolDefinition) {
        if (tool.riskLevel.ordinal > RiskLevel.LOCAL_WRITE.ordinal) {
            if (tool.name !in config.elevatedExtensionAllowList) {
                throw ExtensionElevationException(
                    toolName = tool.name,
                    riskLevel = tool.riskLevel,
                )
            }
        }
    }
}
