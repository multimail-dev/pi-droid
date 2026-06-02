package dev.anthropic.pidroid.demo.simulation

/**
 * A scripted demo scenario that plays through a cross-app orchestration flow
 * without requiring a live LLM API key.
 *
 * Each scenario is a sequence of [Step]s that the [DemoPlayer] executes with
 * realistic timing — streaming text, tool execution cards, confirmation dialogs.
 * The user sees exactly what the real agent loop produces.
 */
data class DemoScenario(
    val id: String,
    val title: String,
    val subtitle: String,
    val userPrompt: String,
    val steps: List<Step>,
) {
    /**
     * A single step in the demo playback.
     */
    sealed class Step {
        /** Stream assistant text character-by-character. */
        data class AssistantStream(
            val text: String,
            val charDelayMs: Long = 18,
        ) : Step()

        /** Show a tool execution card with a simulated result. */
        data class ToolExecution(
            val toolName: String,
            val args: String,
            val result: String,
            val durationMs: Long = 800,
        ) : Step()

        /** Show a confirmation dialog and wait for the user to approve/deny. */
        data class Confirmation(
            val toolName: String,
            val description: String,
            val args: String,
        ) : Step()

        /** Pause between steps. */
        data class Pause(val ms: Long) : Step()
    }

    companion object {
        val ALL = listOf(dinnerPrep(), morningBriefing(), sharePhoto())

        /**
         * "Help me get ready for dinner tonight"
         *
         * Demonstrates: calendar read -> contact lookup -> navigation intent (confirmation gate)
         */
        fun dinnerPrep() = DemoScenario(
            id = "dinner-prep",
            title = "Dinner Prep",
            subtitle = "Calendar, contacts, navigation across 3 apps",
            userPrompt = "Help me get ready for dinner tonight",
            steps = listOf(
                Step.AssistantStream(
                    "Let me check your calendar for tonight's plans."
                ),
                Step.Pause(200),
                Step.ToolExecution(
                    toolName = "read_calendar_events",
                    args = """{"start": "2026-05-08T17:00:00", "end": "2026-05-08T23:59:59"}""",
                    result = """[{"title": "Dinner with Sarah & Mike", "start": "2026-05-08T19:30:00", "end": "2026-05-08T21:30:00", "location": "Osteria Mozza, 6602 Melrose Ave, Los Angeles", "attendees": ["sarah.chen@gmail.com", "mike.torres@gmail.com"]}]""",
                    durationMs = 600,
                ),
                Step.AssistantStream(
                    "You have dinner at **Osteria Mozza** at 7:30 PM with Sarah Chen and Mike Torres. Let me pull up their contact info."
                ),
                Step.Pause(200),
                Step.ToolExecution(
                    toolName = "search_contacts",
                    args = """{"query": "Sarah Chen"}""",
                    result = """[{"name": "Sarah Chen", "phone": "+1-310-555-0142", "email": "sarah.chen@gmail.com"}]""",
                    durationMs = 400,
                ),
                Step.ToolExecution(
                    toolName = "search_contacts",
                    args = """{"query": "Mike Torres"}""",
                    result = """[{"name": "Mike Torres", "phone": "+1-213-555-0198", "email": "mike.torres@gmail.com"}]""",
                    durationMs = 400,
                ),
                Step.AssistantStream(
                    "Got it. Sarah's at +1-310-555-0142 and Mike's at +1-213-555-0198. Let me check your battery and connection before we head out."
                ),
                Step.Pause(200),
                Step.ToolExecution(
                    toolName = "get_battery_state",
                    args = "{}",
                    result = """{"level": 72, "charging": false, "temperature": 28.5}""",
                    durationMs = 300,
                ),
                Step.ToolExecution(
                    toolName = "get_connectivity_state",
                    args = "{}",
                    result = """{"wifi": true, "cellular": true, "ssid": "Home-5G"}""",
                    durationMs = 300,
                ),
                Step.AssistantStream(
                    "Battery at 72% and you're on Wi-Fi — you're good to go. I'll open navigation to Osteria Mozza. This will send an intent to Google Maps."
                ),
                Step.Pause(300),
                Step.Confirmation(
                    toolName = "send_intent",
                    description = "Open Google Maps navigation to Osteria Mozza, 6602 Melrose Ave, Los Angeles",
                    args = """{"action": "android.intent.action.VIEW", "uri": "google.navigation:q=Osteria+Mozza,+6602+Melrose+Ave,+Los+Angeles"}""",
                ),
                Step.AssistantStream(
                    "Navigation is open. Dinner's at 7:30 — enjoy your evening with Sarah and Mike!"
                ),
            ),
        )

        /**
         * "What's going on with my phone?"
         *
         * Demonstrates: device state, installed apps, notifications
         */
        fun morningBriefing() = DemoScenario(
            id = "morning-briefing",
            title = "Morning Briefing",
            subtitle = "Device state, apps, calendar, notifications",
            userPrompt = "Give me a morning briefing",
            steps = listOf(
                Step.AssistantStream(
                    "Good morning! Let me pull together your briefing."
                ),
                Step.Pause(200),
                Step.ToolExecution(
                    toolName = "get_battery_state",
                    args = "{}",
                    result = """{"level": 94, "charging": true, "temperature": 25.1}""",
                    durationMs = 300,
                ),
                Step.ToolExecution(
                    toolName = "get_connectivity_state",
                    args = "{}",
                    result = """{"wifi": true, "cellular": true, "ssid": "Home-5G"}""",
                    durationMs = 300,
                ),
                Step.ToolExecution(
                    toolName = "read_calendar_events",
                    args = """{"start": "2026-05-08T00:00:00", "end": "2026-05-08T23:59:59"}""",
                    result = """[{"title": "Team standup", "start": "2026-05-08T09:00:00", "end": "2026-05-08T09:30:00", "location": "Zoom"}, {"title": "1:1 with Priya", "start": "2026-05-08T11:00:00", "end": "2026-05-08T11:30:00", "location": "Conf Room B"}, {"title": "Dinner with Sarah & Mike", "start": "2026-05-08T19:30:00", "end": "2026-05-08T21:30:00", "location": "Osteria Mozza"}]""",
                    durationMs = 500,
                ),
                Step.ToolExecution(
                    toolName = "read_notifications",
                    args = """{"limit": 5}""",
                    result = """[{"app": "Gmail", "title": "Re: Q2 planning doc", "text": "Priya: Looks great, let's discuss in our 1:1"}, {"app": "Slack", "title": "#eng-general", "text": "Deploy v2.4.1 went out at 11pm — all green"}, {"app": "Weather", "title": "Today's Forecast", "text": "73°F, sunny, 0% chance of rain"}]""",
                    durationMs = 400,
                ),
                Step.AssistantStream(
                    buildString {
                        append("Here's your morning briefing:\n\n")
                        append("**Phone:** 94% and charging on Wi-Fi. You're set.\n\n")
                        append("**Today's schedule:**\n")
                        append("- 9:00 AM — Team standup (Zoom)\n")
                        append("- 11:00 AM — 1:1 with Priya (Conf Room B)\n")
                        append("- 7:30 PM — Dinner with Sarah & Mike (Osteria Mozza)\n\n")
                        append("**Notifications:**\n")
                        append("- Priya replied to the Q2 planning doc — she likes it, wants to discuss at your 1:1\n")
                        append("- Deploy v2.4.1 shipped last night, all green\n")
                        append("- Weather: 73°F and sunny all day\n\n")
                        append("Looks like a solid day. Anything you want me to dig into?")
                    }
                ),
            ),
        )

        /**
         * "Share my ETA with Sarah"
         *
         * Demonstrates: contact lookup -> share_text (confirmation gate)
         */
        fun sharePhoto() = DemoScenario(
            id = "share-eta",
            title = "Share ETA",
            subtitle = "Contact lookup + cross-app share with confirmation",
            userPrompt = "Text Sarah that I'm running 10 minutes late",
            steps = listOf(
                Step.AssistantStream(
                    "Let me find Sarah's contact info so I can send her a message."
                ),
                Step.Pause(200),
                Step.ToolExecution(
                    toolName = "search_contacts",
                    args = """{"query": "Sarah"}""",
                    result = """[{"name": "Sarah Chen", "phone": "+1-310-555-0142", "email": "sarah.chen@gmail.com"}]""",
                    durationMs = 400,
                ),
                Step.AssistantStream(
                    "Found Sarah Chen. I'll share a message with her via the Android share sheet. You'll see a confirmation before anything is sent."
                ),
                Step.Pause(300),
                Step.Confirmation(
                    toolName = "share_text",
                    description = "Share text message via Android share sheet to Sarah Chen",
                    args = """{"text": "Hey Sarah, running about 10 minutes late! Be there soon.", "target_phone": "+1-310-555-0142"}""",
                ),
                Step.AssistantStream(
                    "Done — the share sheet opened with your message to Sarah. She'll know you're on your way."
                ),
            ),
        )
    }
}
