package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.message.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel

/**
 * Drain mode for message queues.
 * ALL: drain all pending messages at once.
 * ONE_AT_A_TIME: drain one message per poll.
 */
enum class DrainMode { ALL, ONE_AT_A_TIME }

/**
 * Queue for steering and follow-up messages.
 * Thread-safe via Channel(UNLIMITED).
 */
class MessageQueue(var drainMode: DrainMode = DrainMode.ALL) {
    private val channel = Channel<Message>(Channel.UNLIMITED)

    fun enqueue(message: Message) {
        channel.trySend(message)
    }

    fun drain(): List<Message> {
        val messages = mutableListOf<Message>()
        when (drainMode) {
            DrainMode.ALL -> {
                var result = channel.tryReceive()
                while (result.isSuccess) {
                    messages.add(result.getOrThrow())
                    result = channel.tryReceive()
                }
            }
            DrainMode.ONE_AT_A_TIME -> {
                val result = channel.tryReceive()
                if (result.isSuccess) {
                    messages.add(result.getOrThrow())
                }
            }
        }
        return messages
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun hasItems(): Boolean = !channel.isEmpty
}
