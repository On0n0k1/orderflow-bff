package com.orderflow.bff.events

import com.orderflow.bff.orderservice.OrderItemDto
import kotlinx.serialization.Serializable

/** Kafka topic order-service publishes OrderCreated events to. */
const val ORDER_CREATED_TOPIC = "orders.created"

/**
 * Mirrors order-service's OrderCreated Kafka payload
 * (see order-service's internal/events.OrderCreated). This is the contract
 * between the two services; keep it in sync with the Go producer.
 */
@Serializable
data class OrderCreatedEvent(
    val orderId: String,
    val customerId: String,
    val items: List<OrderItemDto>,
    val total: Double,
    val timestamp: String,
)
