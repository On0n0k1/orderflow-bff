package com.orderflow.bff.cache

import com.orderflow.bff.summary.OrderSummary
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Testcontainers
class RedisOrderSummaryCacheTest {

    companion object {
        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        private lateinit var redisClient: RedisClient

        @BeforeAll
        @JvmStatic
        fun setUp() {
            redisClient = RedisClient.create(
                RedisURI.create(redis.host, redis.getMappedPort(6379)),
            )
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            redisClient.shutdown()
        }
    }

    private fun newCache() = RedisOrderSummaryCache(redisClient.connect())

    @Test
    fun `put then get round-trips an OrderSummary through real Redis`() = runTest {
        val cache = newCache()
        val summary = OrderSummary(
            orderId = "order-1",
            customerId = "cust-1",
            itemCount = 3,
            total = 42.5,
            createdAt = "2026-01-01T00:00:00Z",
        )

        cache.put(summary)

        assertEquals(summary, cache.get("order-1"))
    }

    @Test
    fun `get returns null for a key that was never cached`() = runTest {
        val cache = newCache()

        assertNull(cache.get("missing-order"))
    }
}
