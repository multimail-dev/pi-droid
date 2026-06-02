package dev.anthropic.pidroid.demo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.anthropic.pidroid.app.PiDroidDemoApp
import dev.anthropic.pidroid.journal.TaskEntry

/**
 * Top-level navigation for the demo app.
 *
 * When the runtime is available (API key configured): Chat, Tasks, Permissions, Settings.
 * When no API key: Demo (simulation), Tasks, Permissions, Settings.
 *
 * The Demo tab runs pre-scripted scenarios that show the full cross-app
 * orchestration flow without requiring a live LLM API key.
 */
@Composable
fun AppNavigation(
    tasks: List<TaskEntry> = emptyList(),
    permissions: List<PermissionItem> = emptyList(),
    onResumeTask: (String) -> Unit = {},
    onPermissionToggle: (String, Boolean) -> Unit = { _, _ -> },
) {
    val runtimeAvailable = PiDroidDemoApp.isInitialized
    val navItems = if (runtimeAvailable) NavItem.liveItems else NavItem.demoItems

    var selectedIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                    )
                }
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            val currentItem = navItems.getOrNull(selectedIndex) ?: navItems.first()
            when (currentItem) {
                NavItem.CHAT -> ChatScreen()
                NavItem.DEMO -> DemoScreen()
                NavItem.TASKS -> TaskDashboardScreen(tasks = tasks, onResumeTask = onResumeTask)
                NavItem.PERMISSIONS -> PermissionCenterScreen(permissions = permissions, onToggle = onPermissionToggle)
                NavItem.SETTINGS -> SettingsScreen()
            }
        }
    }
}

private enum class NavItem(val label: String, val icon: ImageVector) {
    DEMO("Demo", Icons.Default.PlayArrow),
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat),
    TASKS("Tasks", Icons.AutoMirrored.Filled.List),
    PERMISSIONS("Permissions", Icons.Default.Shield),
    SETTINGS("Settings", Icons.Default.Settings);

    companion object {
        /** Nav items when a live API key is configured. */
        val liveItems = listOf(CHAT, DEMO, TASKS, PERMISSIONS, SETTINGS)

        /** Nav items when no API key — demo is the primary tab. */
        val demoItems = listOf(DEMO, TASKS, PERMISSIONS, SETTINGS)
    }
}
