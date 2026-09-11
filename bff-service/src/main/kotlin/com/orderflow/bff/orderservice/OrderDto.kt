package com.orderflow.bff.orderservice

import kotlinx.serialization.Serializable

/**
 * Mirrors order-service's JSON representation of an order
 * (see order-service's internal/order.Order).
 */
@Serializable
data class OrderDto(
    val id: String,
    val customerId: String,
    val items: List<OrderItemDto>,
    val total: Double,
    val createdAt: String,
)

@Serializable
data class OrderItemDto(
    val productId: String,
    val quantity: Int,
    val unitPrice: Double,
)
