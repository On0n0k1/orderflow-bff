package com.orderflow.bff.orderservice

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class OrderServiceClientTest {

    private fun clientWith(engine: MockEngine) = OrderServiceClient(
        httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        },
        baseUrl = "http://order-service",
    )

    @Test
    fun `getOrder returns Found when order-service responds 200`() = runTest {
        val body = """
            {"id":"order-1","customerId":"cust-1","items":[{"productId":"sku-1","quantity":2,"unitPrice":5.0}],"total":10.0,"createdAt":"2026-01-01T00:00:00Z"}
        """.trimIndent()
        val engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }

        val result = clientWith(engine).getOrder("order-1")

        val found = assertIs<OrderLookupResult.Found>(result)
        assertEquals("order-1", found.order.id)
        assertEquals(10.0, found.order.total)
    }

    @Test
    fun `getOrder returns NotFound when order-service responds 404`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }

        val result = clientWith(engine).getOrder("missing")

        assertIs<OrderLookupResult.NotFound>(result)
    }

    @Test
    fun `getOrder throws OrderServiceUnavailableException on 500`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }

        assertFailsWith<OrderServiceUnavailableException> {
            clientWith(engine).getOrder("order-1")
        }
    }

    @Test
    fun `getOrder throws OrderServiceUnavailableException when the transport fails`() = runTest {
        // Regression test: DNS/connection failures like UnresolvedAddressException
        // are not IOExceptions, so the client must catch broadly at this boundary.
        val engine = MockEngine { throw java.nio.channels.UnresolvedAddressException() }

        assertFailsWith<OrderServiceUnavailableException> {
            clientWith(engine).getOrder("order-1")
        }
    }
}
