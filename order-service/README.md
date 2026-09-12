# order-service

Go domain service that owns "orders": accepts orders via REST, persists them, and publishes `OrderCreated` events to Kafka.

See the root [README.md](../README.md) for the overall architecture, the `OrderCreated` event schema, and how to run the full stack.
