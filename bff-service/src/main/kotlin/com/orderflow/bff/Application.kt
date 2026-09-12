package com.orderflow.bff

import com.orderflow.bff.events.ORDER_CREATED_TOPIC
import com.orderflow.bff.events.OrderEventConsumer
import com.orderflow.bff.orderservice.OrderServiceClient
import com.orderflow.bff.orderservice.OrderServiceUnavailableException
import com.orderflow.bff.summary.orderSummaryRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.util.Properties

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    embeddedServer(Netty, port = port) {
        module(orderEventConsumer = createDefaultOrderEventConsumer())
    }.start(wait = true)
}

/**
 * [orderEventConsumer] is left null by default so tests exercising only the
 * HTTP surface don't incidentally spin up a real Kafka client; main() always
 * supplies one.
 */
fun Application.module(
    orderServiceClient: OrderServiceClient = createDefaultOrderServiceClient(),
    orderEventConsumer: OrderEventConsumer? = null,
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(StatusPages) {
        exception<OrderServiceUnavailableException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorResponse(cause.message ?: "failed to reach order-service"))
        }
    }

    if (orderEventConsumer != null) {
        val consumerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        consumerScope.launch { orderEventConsumer.run() }
        monitor.subscribe(ApplicationStopping) {
            orderEventConsumer.wakeup()
            consumerScope.cancel()
        }
    }

    routing {
        get("/health") {
            call.respondText("ok")
        }
        orderSummaryRoutes(orderServiceClient)
    }
}

private fun createDefaultOrderServiceClient(): OrderServiceClient {
    val orderServiceUrl = System.getenv("ORDER_SERVICE_URL") ?: "http://localhost:8080"
    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    return OrderServiceClient(httpClient, orderServiceUrl)
}

private fun createDefaultOrderEventConsumer(): OrderEventConsumer {
    val brokers = System.getenv("KAFKA_BROKERS") ?: "localhost:9092"
    val props = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
        put(ConsumerConfig.GROUP_ID_CONFIG, "bff-service")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
    val logger = LoggerFactory.getLogger(OrderEventConsumer::class.java)
    return OrderEventConsumer(KafkaConsumer(props), ORDER_CREATED_TOPIC) { event ->
        logger.info("received OrderCreated event: {}", event)
    }
}
