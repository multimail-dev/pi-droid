package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageQueueTest {
    @Test
    fun `drain ALL returns all queued messages`() {
        val queue = MessageQueue(DrainMode.ALL)
        queue.enqueue(Message.User("one"))
        queue.enqueue(Message.User("two"))
        queue.enqueue(Message.User("three"))

        val drained = queue.drain()
        assertEquals(3, drained.size)
        assertEquals(
            "one",
            ((drained[0] as Message.User).content.first() as ContentBlock.Text).text,
        )
        assertEquals(
            "two",
            ((drained[1] as Message.User).content.first() as ContentBlock.Text).text,
        )
        assertEquals(
            "three",
            ((drained[2] as Message.User).content.first() as ContentBlock.Text).text,
        )
    }

    @Test
    fun `drain ONE_AT_A_TIME returns one message per call`() {
        val queue = MessageQueue(DrainMode.ONE_AT_A_TIME)
        queue.enqueue(Message.User("one"))
        queue.enqueue(Message.User("two"))

        val first = queue.drain()
        assertEquals(1, first.size)

        val second = queue.drain()
        assertEquals(1, second.size)

        val third = queue.drain()
        assertEquals(0, third.size)
    }

    @Test
    fun `empty queue returns empty list`() {
        val queue = MessageQueue()
        assertEquals(emptyList<Message>(), queue.drain())
    }

    @Test
    fun `hasItems returns true when messages are queued`() {
        val queue = MessageQueue()
        assertFalse(queue.hasItems())
        queue.enqueue(Message.User("test"))
        assertTrue(queue.hasItems())
    }

    @Test
    fun `drain mode can be changed at runtime`() {
        val queue = MessageQueue(DrainMode.ONE_AT_A_TIME)
        queue.enqueue(Message.User("one"))
        queue.enqueue(Message.User("two"))
        queue.enqueue(Message.User("three"))

        // Switch to ALL mode
        queue.drainMode = DrainMode.ALL
        val drained = queue.drain()
        assertEquals(3, drained.size)
    }

    @Test
    fun `drain ALL on empty queue returns empty list`() {
        val queue = MessageQueue(DrainMode.ALL)
        assertEquals(emptyList<Message>(), queue.drain())
    }

    @Test
    fun `drain ONE_AT_A_TIME on empty queue returns empty list`() {
        val queue = MessageQueue(DrainMode.ONE_AT_A_TIME)
        assertEquals(emptyList<Message>(), queue.drain())
    }
}
