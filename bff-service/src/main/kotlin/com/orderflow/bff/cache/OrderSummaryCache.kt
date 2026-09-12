package com.orderflow.bff.cache

import com.orderflow.bff.summary.OrderSummary

/** Read-optimized cache the Kafka consumer fills and the summary route reads from. */
interface OrderSummaryCache {
    suspend fun put(summary: OrderSummary)
    suspend fun get(orderId: String): OrderSummary?
}
