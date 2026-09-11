package com.orderflow.bff.orderservice

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException

/** Outcome of asking order-service for a single order. */
sealed class OrderLookupResult {
    data class Found(val order: OrderDto) : OrderLookupResult()
    data object NotFound : OrderLookupResult()
}

/** Thrown when order-service cannot be reached or returns an unexpected error. */
class OrderServiceUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Talks to order-service's REST API. */
class OrderServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getOrder(id: String): OrderLookupResult {
        val response = try {
            httpClient.get("$baseUrl/orders/$id")
        } catch (e: IOException) {
            throw OrderServiceUnavailableException("failed to reach order-service", e)
        }

        return when (response.status) {
            HttpStatusCode.OK -> OrderLookupResult.Found(response.body())
            HttpStatusCode.NotFound -> OrderLookupResult.NotFound
            else -> throw OrderServiceUnavailableException("order-service returned ${response.status}")
        }
    }
}
