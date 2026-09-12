package com.orderflow.bff.cache

import com.orderflow.bff.summary.OrderSummary
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.serialization.json.Json

/**
 * Redis-backed OrderSummaryCache. Keys are namespaced with a fixed prefix so
 * this can share a Redis instance with other data without colliding. No TTL
 * is set: for this demo's scope, entries live for the process lifetime of
 * Redis rather than expiring, which is a deliberate simplification (see the
 * project README's trade-offs section).
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisOrderSummaryCache(
    connection: StatefulRedisConnection<String, String>,
    private val json: Json = Json,
) : OrderSummaryCache {
    private val commands: RedisCoroutinesCommands<String, String> = connection.coroutines()

    override suspend fun put(summary: OrderSummary) {
        commands.set(key(summary.orderId), json.encodeToString(OrderSummary.serializer(), summary))
    }

    override suspend fun get(orderId: String): OrderSummary? {
        val raw = commands.get(key(orderId)) ?: return null
        return json.decodeFromString(OrderSummary.serializer(), raw)
    }

    private fun key(orderId: String) = "order-summary:$orderId"
}
