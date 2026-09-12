package com.orderflow.bff.summary

import com.orderflow.bff.cache.InMemoryOrderSummaryCache
import com.orderflow.bff.module
import com.orderflow.bff.orderservice.OrderServiceClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SummaryRoutesTest {

    private fun orderServiceClientWith(engine: MockEngine) = OrderServiceClient(
        httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        },
        baseUrl = "http://order-service",
    )

    @Test
    fun `order-summary reshapes the order-service response`() = testApplication {
        val body = """
            {"id":"order-1","customerId":"cust-1","items":[{"productId":"sku-1","quantity":2,"unitPrice":5.0},{"productId":"sku-2","quantity":1,"unitPrice":3.0}],"total":13.0,"createdAt":"2026-01-01T00:00:00Z"}
        """.trimIndent()
        val engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }

        application { module(orderServiceClientWith(engine)) }

        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val response = client.get("/order-summary/order-1")

        assertEquals(HttpStatusCode.OK, response.status)
        val summary = response.body<OrderSummary>()
        assertEquals("order-1", summary.orderId)
        assertEquals(3, summary.itemCount)
        assertEquals(13.0, summary.total)
    }

    @Test
    fun `order-summary returns 404 when order-service has no such order`() = testApplication {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        application { module(orderServiceClientWith(engine)) }

        val response = createClient { }.get("/order-summary/missing")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `order-summary returns 502 when order-service is unreachable`() = testApplication {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        application { module(orderServiceClientWith(engine)) }

        val response = createClient { }.get("/order-summary/order-1")

        assertEquals(HttpStatusCode.BadGateway, response.status)
    }

    @Test
    fun `order-summary serves from cache without calling order-service`() = testApplication {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respondError(HttpStatusCode.InternalServerError)
        }
        val cache = InMemoryOrderSummaryCache()
        val cached = OrderSummary(orderId = "order-1", customerId = "cust-1", itemCount = 2, total = 20.0, createdAt = "2026-01-01T00:00:00Z")
        cache.put(cached)

        application { module(orderServiceClientWith(engine), cache = cache) }

        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val response = client.get("/order-summary/order-1")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(cached, response.body<OrderSummary>())
        assertEquals(0, requestCount)
    }

    @Test
    fun `order-summary falls back to order-service on a cache miss and warms the cache`() = testApplication {
        val body = """
            {"id":"order-2","customerId":"cust-2","items":[{"productId":"sku-1","quantity":1,"unitPrice":7.0}],"total":7.0,"createdAt":"2026-01-01T00:00:00Z"}
        """.trimIndent()
        val engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val cache = InMemoryOrderSummaryCache()

        application { module(orderServiceClientWith(engine), cache = cache) }

        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val response = client.get("/order-summary/order-2")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(7.0, response.body<OrderSummary>().total)

        val warmed = cache.get("order-2")
        assertEquals("order-2", warmed?.orderId)
    }
}
