package dev.anthropic.pidroid.demo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.anthropic.pidroid.demo.simulation.DemoPlayer
import dev.anthropic.pidroid.demo.simulation.DemoScenario
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Demo mode entry point.
 *
 * Shows a scenario picker when no scenario is active. When a scenario is
 * selected, plays it through the chat UI with realistic timing. No API key
 * required — everything is pre-scripted.
 */
@Composable
fun DemoScreen() {
    val player = remember { DemoPlayer() }
    val isPlaying by player.isPlaying.collectAsState()
    var activeScenario by remember { mutableStateOf<DemoScenario?>(null) }
    val scope = rememberCoroutineScope()
    var playJob by remember { mutableStateOf<Job?>(null) }

    if (activeScenario != null && (isPlaying || player.messages.collectAsState().value.isNotEmpty())) {
        DemoPlaybackScreen(
            scenario = activeScenario!!,
            player = player,
            onBack = {
                playJob?.cancel()
                playJob = null
                player.reset()
                activeScenario = null
            },
        )
    } else {
        ScenarioPickerScreen(
            scenarios = DemoScenario.ALL,
            onSelect = { scenario ->
                activeScenario = scenario
                playJob = scope.launch { player.play(scenario) }
            },
        )
    }
}

@Composable
private fun ScenarioPickerScreen(
    scenarios: List<DemoScenario>,
    onSelect: (DemoScenario) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Pi-Droid Demo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "See cross-app orchestration in action. No API key needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        scenarios.forEach { scenario ->
            ScenarioCard(scenario = scenario, onClick = { onSelect(scenario) })
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ScenarioCard(
    scenario: DemoScenario,
    onClick: () -> Unit,
) {
    val icon = when (scenario.id) {
        "dinner-prep" -> Icons.Default.CalendarMonth
        "morning-briefing" -> Icons.Default.Info
        "share-eta" -> Icons.Default.Share
        else -> Icons.Default.Info
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scenario.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = scenario.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "\"${scenario.userPrompt}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoPlaybackScreen(
    scenario: DemoScenario,
    player: DemoPlayer,
    onBack: () -> Unit,
) {
    val messages by player.messages.collectAsState()
    val streamingText by player.streamingText.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val pendingConfirmation by player.pendingConfirmation.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(scenario.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            // Simulated badge
            DemoBadge()

            // Message list (reuses existing composables)
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }

                if (streamingText.isNotEmpty()) {
                    item {
                        StreamingTextBlock(text = streamingText)
                    }
                }

                if (isPlaying && streamingText.isEmpty() && messages.isNotEmpty()) {
                    val lastMsg = messages.last()
                    val isWaitingForConfirmation = pendingConfirmation != null
                    if (!isWaitingForConfirmation) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // Auto-scroll
            LaunchedEffect(messages.size, streamingText) {
                if (messages.isNotEmpty() || streamingText.isNotEmpty()) {
                    listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                }
            }

            // Confirmation dialog
            pendingConfirmation?.let { confirmation ->
                DemoConfirmationDialog(
                    confirmation = confirmation,
                    onApprove = { player.resolveConfirmation(true) },
                    onDeny = { player.resolveConfirmation(false) },
                )
            }

            // Replay / back controls
            AnimatedVisibility(
                visible = !isPlaying && messages.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Pick another")
                    }
                    val scope = rememberCoroutineScope()
                    Button(
                        onClick = {
                            scope.launch { player.play(scenario) }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Replay")
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoBadge() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        ) {
            Text(
                text = "SIMULATION MODE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DemoConfirmationDialog(
    confirmation: dev.anthropic.pidroid.demo.simulation.PendingConfirmation,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text("Confirm Action") },
        text = {
            Column {
                Text(
                    text = confirmation.toolName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = confirmation.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (confirmation.args.isNotEmpty() && confirmation.args != "{}") {
                    Text(
                        text = confirmation.args,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onApprove) {
                Text("Approve")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDeny) {
                Text("Deny")
            }
        },
    )
}
