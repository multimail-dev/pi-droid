package dev.anthropic.pidroid.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthropic.pidroid.journal.TaskEntry
import dev.anthropic.pidroid.journal.TaskStatus

/**
 * Task dashboard showing interrupted and parked tasks.
 * Allows resuming interrupted tasks.
 */
@Composable
fun TaskDashboardScreen(
    tasks: List<TaskEntry>,
    onResumeTask: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Task Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (tasks.isEmpty()) {
            Text(
                text = "No pending tasks",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks) { task ->
                    TaskCard(task = task, onResume = { onResumeTask(task.id) })
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntry, onResume: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (task.status) {
                TaskStatus.INTERRUPTED -> MaterialTheme.colorScheme.errorContainer
                TaskStatus.PARKED -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.prompt.take(60) + if (task.prompt.length > 60) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${task.status.name} • Step ${task.stepIndex}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (task.status == TaskStatus.INTERRUPTED) {
                Button(onClick = onResume) {
                    Text("Resume")
                }
            }
        }
    }
}
