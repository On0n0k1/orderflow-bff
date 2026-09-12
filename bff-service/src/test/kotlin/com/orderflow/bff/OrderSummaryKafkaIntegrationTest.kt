package com.orderflow.bff

import com.orderflow.bff.cache.InMemoryOrderSummaryCache
import com.orderflow.bff.events.ORDER_CREATED_TOPIC
import com.orderflow.bff.events.OrderCreatedEvent
import com.orderflow.bff.events.OrderEventConsumer
import com.orderflow.bff.orderservice.OrderItemDto
import com.orderflow.bff.orderservice.OrderServiceClient
import com.orderflow.bff.summary.OrderSummary
import com.orderflow.bff.summary.toSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.Properties
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Proves the event-driven boundary the whole project is built around: an
 * OrderCreated event published directly to Kafka (bypassing order-service
 * entirely) ends up reflected by the BFF's cache-backed read endpoint, via
 * the real Kafka consumer — not a direct call to order-service.
 */
@Testcontainers
class OrderSummaryKafkaIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("apache/kafka:4.3.0"))
    }

    private fun publishOrderCreated(event: OrderCreatedEvent) {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        }
        KafkaProducer<String, String>(props).use { producer ->
            val payload = Json.encodeToString(OrderCreatedEvent.serializer(), event)
            producer.send(ProducerRecord(ORDER_CREATED_TOPIC, event.orderId, payload)).get()
        }
    }

    private fun consumerPointedAtTestBroker(onEvent: suspend (OrderCreatedEvent) -> Unit): OrderEventConsumer {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "bff-service-test-${UUID.randomUUID()}")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        }
        return OrderEventConsumer(KafkaConsumer(props), ORDER_CREATED_TOPIC, onEvent = onEvent)
    }

    /** A client that always 404s, so a bug that falls through to order-service fails loudly instead of masking as a false pass. */
    private fun unreachableOrderServiceClient() = OrderServiceClient(
        httpClient = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }) {
            install(ContentNegotiation) { json() }
        },
        baseUrl = "http://order-service-should-not-be-called",
    )

    @Test
    fun `order-summary reflects an OrderCreated event published directly to Kafka`() = testApplication {
        val cache = InMemoryOrderSummaryCache()
        val event = OrderCreatedEvent(
            orderId = "order-kafka-1",
            customerId = "cust-kafka",
            items = listOf(OrderItemDto(productId = "sku-1", quantity = 3, unitPrice = 4.0)),
            total = 12.0,
            timestamp = "2026-01-01T00:00:00Z",
        )

        publishOrderCreated(event)

        val consumer = consumerPointedAtTestBroker { received -> cache.put(received.toSummary()) }
        application {
            module(
                orderServiceClient = unreachableOrderServiceClient(),
                orderEventConsumer = consumer,
                cache = cache,
            )
        }

        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

        val summary = withTimeout(15.seconds) {
            var result: OrderSummary? = null
            while (result == null) {
                val response = client.get("/order-summary/order-kafka-1")
                if (response.status == HttpStatusCode.OK) {
                    result = response.body<OrderSummary>()
                } else {
                    delay(200.milliseconds)
                }
            }
            result
        }

        assertEquals("order-kafka-1", summary.orderId)
        assertEquals("cust-kafka", summary.customerId)
        assertEquals(3, summary.itemCount)
        assertEquals(12.0, summary.total)
    }
}
