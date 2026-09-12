# bff-service

Kotlin (Ktor) Backend-for-Frontend that consumes `OrderCreated` events from Kafka, maintains a read-optimized cache, and exposes a frontend-shaped REST API over order data.

See the root [README.md](../README.md) for the overall architecture, the `OrderCreated` event schema, and how to run the full stack.
