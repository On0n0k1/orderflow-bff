package com.orderflow.bff.cache

import com.orderflow.bff.summary.OrderSummary
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory OrderSummaryCache. Used as the safe default (no external
 * dependency, nothing to fail to connect to) so callers that don't care
 * about caching — like most unit tests — don't need a real Redis instance.
 * Production wiring in main() uses RedisOrderSummaryCache instead.
 */
class InMemoryOrderSummaryCache : OrderSummaryCache {
    private val entries = ConcurrentHashMap<String, OrderSummary>()

    override suspend fun put(summary: OrderSummary) {
        entries[summary.orderId] = summary
    }

    override suspend fun get(orderId: String): OrderSummary? = entries[orderId]
}
