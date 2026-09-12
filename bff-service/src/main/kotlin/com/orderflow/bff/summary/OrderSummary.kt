package com.orderflow.bff.summary

import com.orderflow.bff.events.OrderCreatedEvent
import com.orderflow.bff.orderservice.OrderDto
import kotlinx.serialization.Serializable

/**
 * The frontend-shaped view of an order. This is deliberately not a 1:1 copy
 * of order-service's Order representation: it drops fields the frontend
 * doesn't need (the raw line items) and adds a computed itemCount so the
 * client doesn't have to derive it itself.
 */
@Serializable
data class OrderSummary(
    val orderId: String,
    val customerId: String,
    val itemCount: Int,
    val total: Double,
    val createdAt: String,
)

fun OrderDto.toSummary(): OrderSummary = OrderSummary(
    orderId = id,
    customerId = customerId,
    itemCount = items.sumOf { it.quantity },
    total = total,
    createdAt = createdAt,
)

fun OrderCreatedEvent.toSummary(): OrderSummary = OrderSummary(
    orderId = orderId,
    customerId = customerId,
    itemCount = items.sumOf { it.quantity },
    total = total,
    createdAt = timestamp,
)
