package dev.anthropic.pidroid.demo.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.anthropic.pidroid.app.PiDroidDemoApp
import dev.anthropic.pidroid.llm.registry.ModelInfo
import dev.anthropic.pidroid.llm.registry.ModelRegistry

/**
 * Settings screen for LLM provider, model, and API key configuration.
 *
 * Uses [ModelRegistry] to populate provider and model dropdowns with all
 * known providers and models. Writes to EncryptedSharedPreferences.
 * Changes require app restart because PiRuntime is a process-level
 * singleton with no re-init path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { PiDroidDemoApp.getSecurePrefs(context) }

    if (prefs == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Storage Error",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Unable to access secure or regular storage. " +
                    "API keys cannot be saved on this device.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    // Initialize registry if needed (safe to call multiple times — no-op after first)
    remember {
        try {
            ModelRegistry.init(context)
        } catch (_: Exception) {
            // Already initialized or resource unavailable
        }
        true
    }

    // Load available providers and models from registry
    val providers = remember {
        try {
            ModelRegistry.getProviders().sorted()
        } catch (_: IllegalStateException) {
            listOf("anthropic", "openai") // Fallback
        }
    }

    val savedProvider = prefs.getString(PiDroidDemoApp.PREF_PROVIDER, "anthropic") ?: "anthropic"
    val savedApiKey = prefs.getString(PiDroidDemoApp.PREF_API_KEY, "") ?: ""
    val savedModel = prefs.getString(PiDroidDemoApp.PREF_MODEL, "") ?: ""
    val savedBaseUrl = prefs.getString(PiDroidDemoApp.PREF_BASE_URL, "") ?: ""

    var selectedProvider by remember { mutableStateOf(savedProvider) }
    var apiKey by remember { mutableStateOf(savedApiKey) }

    val modelsForProvider = remember(selectedProvider) {
        try {
            ModelRegistry.getModels(selectedProvider)
        } catch (_: IllegalStateException) {
            emptyList()
        }
    }

    var selectedModel by remember(selectedProvider) {
        val default = if (savedProvider == selectedProvider && savedModel.isNotBlank()) {
            savedModel
        } else {
            modelsForProvider.firstOrNull()?.id ?: PiDroidDemoApp.defaultModelFor(selectedProvider)
        }
        mutableStateOf(default)
    }

    var baseUrl by remember { mutableStateOf(savedBaseUrl) }
    var showAdvanced by remember { mutableStateOf(savedBaseUrl.isNotBlank()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "LLM Provider Settings",
            style = MaterialTheme.typography.headlineMedium,
        )

        if (PiDroidDemoApp.isInitialized) {
            Text(
                text = "Runtime is active. Changes require app restart.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Provider dropdown
        Text(text = "Provider", style = MaterialTheme.typography.titleMedium)
        ProviderDropdown(
            providers = providers,
            selected = selectedProvider,
            onSelected = { provider ->
                selectedProvider = provider
                val models = try {
                    ModelRegistry.getModels(provider)
                } catch (_: IllegalStateException) {
                    emptyList()
                }
                selectedModel = models.firstOrNull()?.id
                    ?: PiDroidDemoApp.defaultModelFor(provider)
            },
        )

        // Model dropdown
        Text(text = "Model", style = MaterialTheme.typography.titleMedium)
        ModelDropdown(
            models = modelsForProvider,
            selectedModelId = selectedModel,
            onSelected = { modelId -> selectedModel = modelId },
        )

        // API Key
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        // Advanced / Base URL toggle
        if (!showAdvanced) {
            TextButton(onClick = { showAdvanced = true }) {
                Text("Advanced settings")
            }
        } else {
            Text(text = "Base URL Override", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    val registryUrl = modelsForProvider
                        .find { it.id == selectedModel }?.baseUrl
                    Text(registryUrl ?: "https://api.example.com/v1")
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    prefs.edit()
                        .putString(PiDroidDemoApp.PREF_PROVIDER, selectedProvider)
                        .putString(PiDroidDemoApp.PREF_API_KEY, apiKey)
                        .putString(
                            PiDroidDemoApp.PREF_MODEL,
                            selectedModel.ifBlank { PiDroidDemoApp.defaultModelFor(selectedProvider) },
                        )
                        .putString(PiDroidDemoApp.PREF_BASE_URL, baseUrl.ifBlank { null })
                        .apply()
                    Toast.makeText(context, "Saved. Restart app to apply.", Toast.LENGTH_LONG).show()
                },
                enabled = apiKey.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Save")
            }

            OutlinedButton(
                onClick = {
                    prefs.edit()
                        .remove(PiDroidDemoApp.PREF_API_KEY)
                        .remove(PiDroidDemoApp.PREF_PROVIDER)
                        .remove(PiDroidDemoApp.PREF_MODEL)
                        .remove(PiDroidDemoApp.PREF_BASE_URL)
                        .apply()
                    apiKey = ""
                    selectedProvider = "anthropic"
                    selectedModel = PiDroidDemoApp.defaultModelFor("anthropic")
                    baseUrl = ""
                    showAdvanced = false
                    Toast.makeText(context, "Cleared. Restart app to apply.", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Clear")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    providers: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onSelected(provider)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<ModelInfo>,
    selectedModelId: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = models.find { it.id == selectedModelId }
    val displayText = selectedModel?.let { "${it.name} (${it.id})" }
        ?: selectedModelId.ifBlank { "No models available" }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text("${model.name} (${model.id})") },
                    onClick = {
                        onSelected(model.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
