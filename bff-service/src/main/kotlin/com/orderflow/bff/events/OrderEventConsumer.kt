package com.orderflow.bff.events

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Polls [topic] for OrderCreated events and forwards each one to [onEvent].
 * A single record that fails to deserialize is logged and skipped rather
 * than blocking the whole partition.
 */
class OrderEventConsumer(
    private val consumer: Consumer<String, String>,
    private val topic: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onEvent: suspend (OrderCreatedEvent) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(OrderEventConsumer::class.java)

    /** Runs the poll loop until cancelled or [wakeup] is called. Closes the consumer on exit. */
    suspend fun run() {
        consumer.subscribe(listOf(topic))
        try {
            while (currentCoroutineContext().isActive) {
                val records = withContext(Dispatchers.IO) { consumer.poll(Duration.ofMillis(500)) }
                for (record in records) {
                    try {
                        onEvent(json.decodeFromString(OrderCreatedEvent.serializer(), record.value()))
                    } catch (e: Exception) {
                        logger.error("failed to process OrderCreated record at offset {}", record.offset(), e)
                    }
                }
            }
        } catch (e: WakeupException) {
            // Expected: triggered by wakeup() during shutdown.
        } finally {
            consumer.close()
        }
    }

    /** Interrupts a blocked poll() call so [run] can exit gracefully. */
    fun wakeup() = consumer.wakeup()
}
