package dev.anthropic.pidroid.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Main chat surface composable.
 *
 * Displays message list, streaming text, and input field.
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    PermissionGate {
        ChatContent(viewModel)
    }
}

@Composable
private fun ChatContent(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Message list
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

            // Streaming text indicator
            if (streamingText.isNotEmpty()) {
                item {
                    StreamingTextBlock(text = streamingText)
                }
            }

            // Loading indicator
            if (isProcessing && streamingText.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        // Auto-scroll to bottom
        LaunchedEffect(messages.size, streamingText) {
            if (messages.isNotEmpty() || streamingText.isNotEmpty()) {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }

        // Confirmation dialog
        pendingConfirmation?.let { request ->
            ConfirmationDialog(
                request = request,
                onApprove = { viewModel.approveConfirmation(request.requestId) },
                onDeny = { viewModel.denyConfirmation(request.requestId) },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input field
        ChatInput(
            isProcessing = isProcessing,
            onSend = { viewModel.sendMessage(it) },
            onCancel = { viewModel.cancelAgent() },
        )
    }
}

@Composable
private fun ChatInput(
    isProcessing: Boolean,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask Pi anything...") },
            singleLine = true,
            enabled = !isProcessing,
        )

        if (isProcessing) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        } else {
            Button(
                onClick = {
                    onSend(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Send")
            }
        }
    }
}
