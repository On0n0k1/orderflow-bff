package com.orderflow.bff.events

import kotlinx.coroutines.test.runTest
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.common.TopicPartition
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderEventConsumerTest {

    private val topic = "orders.created"
    private val partition = TopicPartition(topic, 0)

    @Test
    fun `run deserializes records and forwards them to onEvent`() = runTest {
        val mockConsumer = MockConsumer<String, String>("earliest")
        mockConsumer.subscribe(listOf(topic))
        mockConsumer.rebalance(listOf(partition))
        mockConsumer.updateBeginningOffsets(mapOf(partition to 0L))

        val received = mutableListOf<OrderCreatedEvent>()
        val consumer = OrderEventConsumer(mockConsumer, topic) { event -> received.add(event) }

        val body = """
            {"orderId":"order-1","customerId":"cust-1","items":[{"productId":"sku-1","quantity":2,"unitPrice":5.0}],"total":10.0,"timestamp":"2026-01-01T00:00:00Z"}
        """.trimIndent()

        mockConsumer.schedulePollTask {
            mockConsumer.addRecord(ConsumerRecord(topic, 0, 0L, "order-1", body))
        }
        mockConsumer.schedulePollTask {
            consumer.wakeup()
        }

        consumer.run()

        assertEquals(1, received.size)
        assertEquals("order-1", received[0].orderId)
        assertEquals(10.0, received[0].total)
    }

    @Test
    fun `run skips a malformed record and keeps processing`() = runTest {
        val mockConsumer = MockConsumer<String, String>("earliest")
        mockConsumer.subscribe(listOf(topic))
        mockConsumer.rebalance(listOf(partition))
        mockConsumer.updateBeginningOffsets(mapOf(partition to 0L))

        val received = mutableListOf<OrderCreatedEvent>()
        val consumer = OrderEventConsumer(mockConsumer, topic) { event -> received.add(event) }

        val validBody = """
            {"orderId":"order-2","customerId":"cust-2","items":[],"total":0.0,"timestamp":"2026-01-01T00:00:00Z"}
        """.trimIndent()

        mockConsumer.schedulePollTask {
            mockConsumer.addRecord(ConsumerRecord(topic, 0, 0L, "bad", "{not json"))
            mockConsumer.addRecord(ConsumerRecord(topic, 0, 1L, "order-2", validBody))
        }
        mockConsumer.schedulePollTask {
            consumer.wakeup()
        }

        consumer.run()

        assertEquals(1, received.size)
        assertEquals("order-2", received[0].orderId)
    }
}
