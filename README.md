# Pi-Droid

Pi-Droid is our modest attempt to bring pi to Android. We took [pi-ai](https://github.com/badlogic/pi-mono/tree/main/packages/ai)'s provider system and [pi-agent-core](https://github.com/badlogic/pi-mono/tree/main/packages/agent)'s agent loop, rewrote them in Kotlin, and connected them to the system APIs that make a phone agent worth having: calendars, contacts, intents, share sheets, notifications. The architecture is Mario's. We just carried it to a new platform and tried not to mess it up.

If you didn't already know: Mario Zechner ([@badlogic](https://github.com/badlogic)) built [pi](https://github.com/badlogic/pi-mono), a single abstraction across 22 LLM providers, and a registry of 750+ models + an agent loop that handles tool calls, confirmation gates, and multi-turn conversations.

## What it does

Configure an LLM provider, declare Android capabilities, and register tool handlers. The runtime manages the conversation loop, executes tool calls, and gates cross-app actions behind user confirmation.

```kotlin
val config = PiRuntimeConfig(
    llmProvider = LlmProviderConfig(
        provider = "anthropic",
        modelId = "claude-sonnet-4-20250514",
        apiKey = apiKey,
    ),
    capabilities = listOf(
        CapabilityGrant("android.permission.READ_CALENDAR"),
        CapabilityGrant("android.permission.READ_CONTACTS"),
    ),
    systemPrompt = "You are a helpful assistant on an Android phone.",
)

val runtime = PiRuntime.initialize(context, config, handlers)
runtime.sendPrompt("Help me get ready for dinner tonight")
```

## Providers

The model registry is exported from pi-ai's `MODELS` and bundled as a JSON resource. 22 providers, 750+ models.

| API Type | Providers | Origin |
|----------|-----------|--------|
| Anthropic Messages | Anthropic | pi-ai `anthropic-messages` |
| OpenAI Completions | OpenAI, Groq, Together, Cerebras, etc. | pi-ai `openai-completions` |
| OpenAI Responses | OpenAI | pi-ai `openai-responses` |
| Google Generative AI | Google | pi-ai `google-generative-ai` |
| Mistral | Mistral | pi-ai `mistral-conversations` |

Provider dispatch works by API type, not provider name. This matches pi-ai's design where Groq, Together, and Cerebras all route through `openai-completions`. Unknown models with a `baseUrl` fall back to `openai-completions` for Ollama/self-hosted use.

The `OpenAiCompat` settings (maxTokensField, requiresToolResultName, etc.) are ported from pi-ai's `OpenAICompletionsCompat` interface.

## Tools

14 built-in tool handlers (presently | contributions welcome):

| Category | Tools |
|----------|-------|
| Calendar | `read_calendar_events`, `create_calendar_event` |
| Contacts | `search_contacts`, `get_contact_details` |
| Device | `get_battery_state`, `get_connectivity_state`, `get_installed_apps` |
| Intents | `launch_app`, `open_url`, `send_intent`, `share_text`, `set_alarm` |
| Notifications | `read_notifications`, `get_notification_channels` |

Tools that cross app boundaries (`send_intent`, `share_text`) require user confirmation before execution.

## Architecture

```
PiRuntime (singleton, process-level)
  ModelRegistry       750+ models from pi-ai, parsed once from JSON
  ProviderFactory     routes by API type to LlmProvider implementations
  AgentLoop           turn-based: prompt > LLM > tool calls > results > repeat
  ToolExecutor        dispatches to ToolHandler implementations
  ConfirmationGate    suspends the loop until the UI approves/denies
  MessageTransformer  cross-provider message replay (ported from pi-ai)
```

`PiRuntime.initialize()` runs once per process. Config changes require app restart. This is intentional.

## Demo app

The `app/` module includes a demo with three pre-scripted scenarios that run without an API key:

- **Dinner Prep**: calendar > contacts > device state > navigation intent
- **Morning Briefing**: device state > calendar > notifications
- **Share ETA**: contacts > share sheet

Configure a real API key in Settings to use the live agent.

## Project structure

```
library/          Android library (the SDK)
  src/main/
    kotlin/       Runtime, agent loop, providers, tools, registry
    res/raw/      pi_ai_models.json (exported from pi-ai)
  src/test/       420 unit tests
app/              Demo app
  src/main/       Compose UI, simulation engine
```

## Requirements

- Android API 31+ 
- Kotlin 2.1+
- An API key from any supported provider (for live use)

## Credits

- [pi-mono](https://github.com/badlogic/pi-mono) by [@badlogic](https://github.com/badlogic) (Mario Zechner) for the LLM abstraction, agent runtime, and model registry that this project ports

## License

Apache 2.0
