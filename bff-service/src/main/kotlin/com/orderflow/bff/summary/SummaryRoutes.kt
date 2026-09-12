package com.orderflow.bff.summary

import com.orderflow.bff.ErrorResponse
import com.orderflow.bff.cache.OrderSummaryCache
import com.orderflow.bff.orderservice.OrderLookupResult
import com.orderflow.bff.orderservice.OrderServiceClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * GET /order-summary/{id}: a frontend-shaped read endpoint. Reads from the
 * Kafka-fed cache first, since that's the fast path with no synchronous call
 * to order-service. A cache miss isn't necessarily a nonexistent order — the
 * Kafka consumer may simply not have caught up yet — so it falls back to a
 * direct call to order-service, and warms the cache from that result so the
 * next read hits the fast path. This fallback (and its staleness trade-off)
 * is a deliberate design decision; see the project README.
 */
fun Route.orderSummaryRoutes(client: OrderServiceClient, cache: OrderSummaryCache) {
    get("/order-summary/{id}") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("order id is required"))
            return@get
        }

        val cached = cache.get(id)
        if (cached != null) {
            call.respond(HttpStatusCode.OK, cached)
            return@get
        }

        when (val result = client.getOrder(id)) {
            is OrderLookupResult.Found -> {
                val summary = result.order.toSummary()
                cache.put(summary)
                call.respond(HttpStatusCode.OK, summary)
            }
            is OrderLookupResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("order not found"))
        }
    }
}
