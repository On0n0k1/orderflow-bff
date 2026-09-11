package com.orderflow.bff.summary

import com.orderflow.bff.ErrorResponse
import com.orderflow.bff.orderservice.OrderLookupResult
import com.orderflow.bff.orderservice.OrderServiceClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * GET /order-summary/{id}: a frontend-shaped read endpoint, currently backed
 * by a direct call to order-service. A later change wires this up to read
 * from a Kafka-fed cache first, falling back to this direct call on a miss.
 */
fun Route.orderSummaryRoutes(client: OrderServiceClient) {
    get("/order-summary/{id}") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("order id is required"))
            return@get
        }

        when (val result = client.getOrder(id)) {
            is OrderLookupResult.Found -> call.respond(HttpStatusCode.OK, result.order.toSummary())
            is OrderLookupResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("order not found"))
        }
    }
}
