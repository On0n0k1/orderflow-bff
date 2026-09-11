# orderflow-bff

A polyglot microservices demo: a Go domain service that owns order data and publishes domain events to Kafka, and a Kotlin (Ktor) Backend-for-Frontend (BFF) that consumes those events, maintains a read-optimized cache, and exposes a frontend-shaped REST API.

This README will be filled in as the project is built, with the full architecture diagram, event schema, and design decisions & trade-offs.

## Layout

- [`order-service/`](order-service/) — Go domain service for orders.
- [`bff-service/`](bff-service/) — Kotlin/Ktor Backend-for-Frontend.
