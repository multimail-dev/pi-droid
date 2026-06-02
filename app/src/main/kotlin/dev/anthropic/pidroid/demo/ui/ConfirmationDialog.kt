package dev.anthropic.pidroid.demo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthropic.pidroid.tools.ConfirmationRequest

/**
 * Confirmation dialog shown when a tool requires user approval.
 *
 * Displays tool name, description, and a preview of the arguments.
 * Approve/Deny buttons control tool execution.
 */
@Composable
fun ConfirmationDialog(
    request: ConfirmationRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Text("Confirm Action")
        },
        text = {
            Column {
                Text(
                    text = request.toolName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = request.toolDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (request.arguments.isNotEmpty() && request.arguments != "{}") {
                    Text(
                        text = request.arguments,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                    )
                }
                if (request.requiresBiometric) {
                    Text(
                        text = "⚠️ Biometric verification required",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
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
