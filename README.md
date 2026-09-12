# orderflow-bff

A small polyglot microservices demo: a **Go** domain service that owns order data and publishes domain events to Kafka, and a **Kotlin (Ktor)** Backend-for-Frontend (BFF) that consumes those events, maintains a read-optimized Redis cache, and exposes a frontend-shaped REST API — connected by an event stream rather than synchronous calls alone. It's a portfolio/reference project, not a production system: it explicitly does **not** implement authentication, a multi-service event mesh, a frontend UI, production-grade Kafka configuration (replication, partitioning, exactly-once semantics), or Kubernetes manifests — see [Deliberately out of scope](#deliberately-out-of-scope) for the reasoning behind each.

## Architecture

```
WRITE PATH (order creation)
───────────────────────────
client
  │  POST /orders
  ▼
order-service (Go)
  │  1. validate + persist (in-memory store)
  │  2. publish OrderCreated
  ▼
Kafka topic: orders.created


READ PATH (BFF serving a summary)
──────────────────────────────────
Background, continuous:
  Kafka topic: orders.created
    ▼  consumed asynchronously
  bff-service Kafka consumer
    ▼  reshapes event → OrderSummary
  Redis cache

Per request — client calls GET /order-summary/{id} on bff-service, which checks Redis first:

  cache hit:   Redis cache ──▶ respond immediately

  cache miss:  bff-service ──GET /orders/{id}──▶ order-service
                   ──▶ reshape response ──▶ warm the Redis cache ──▶ respond
```

- **order-service** is the source of truth: it validates, persists (in-memory), and publishes.
- **bff-service** never proxies 1:1 — `/order-summary/{id}` reshapes the order into a frontend-shaped payload (drops raw line items, adds a computed `itemCount`) and prefers the cache over a synchronous call.
- The two services never call each other on the *write* path. The *read* path's direct call to order-service exists only as a fallback for cache misses (see [Cache staleness & fallback](#cache-staleness--fallback)).

## `OrderCreated` event schema

Published by order-service to the `orders.created` Kafka topic (key = order ID) whenever an order is created. bff-service depends on this exact shape:

```json
{
  "orderId": "498c1678-b639-494d-9cc8-1016002499c6",
  "customerId": "cust-42",
  "items": [
    { "productId": "sku-1", "quantity": 2, "unitPrice": 9.99 }
  ],
  "total": 19.98,
  "timestamp": "2026-09-12T08:24:27.606942675Z"
}
```

| Field        | Type              | Notes                                                    |
|--------------|-------------------|-----------------------------------------------------------|
| `orderId`    | string            | UUID assigned by order-service.                           |
| `customerId` | string            | Opaque customer identifier, as supplied by the client.     |
| `items`      | array             | `{ productId: string, quantity: int, unitPrice: number }` |
| `total`      | number            | `sum(quantity * unitPrice)`, computed server-side.         |
| `timestamp`  | string (RFC 3339) | When the order was persisted.                              |

The same shape (minus the Kafka envelope) is what `GET /orders/{id}` returns from order-service.

## API reference

**order-service** (default port `8080`):
- `POST /orders` — body: `{ customerId, items: [{ productId, quantity, unitPrice }] }`. Rejects empty item lists, non-positive quantities, and negative unit prices with `400` and a list of issues. Returns `201` with the persisted order (`total` is always server-computed, never trusted from the client).
- `GET /orders/{id}` — `200` with the order, or `404`.
- `GET /health`

**bff-service** (default port `8081`):
- `GET /order-summary/{id}` — `200` with `{ orderId, customerId, itemCount, total, createdAt }`, `404` if the order doesn't exist anywhere, `502` if order-service is unreachable during a fallback call.
- `GET /health`

## Design decisions & trade-offs

**Why a BFF here, and what it reshapes.** A raw proxy would just forward order-service's JSON. `/order-summary/{id}` instead computes `itemCount` from the line items and drops the raw items array — a small example, but it's the same shape of work a real BFF does: aggregate and adapt a domain service's representation into whatever the calling frontend actually needs, so the frontend isn't stuck consuming a backend-shaped payload or duplicating derivation logic client-side.

**Cache staleness & fallback.** The BFF's whole point is serving reads without a synchronous call to order-service — but Kafka consumption is asynchronous, so a read can legitimately arrive before the consumer has caught up (consumer lag), especially right after an order is created. Rather than surface that as an error, a cache miss falls back to calling order-service directly, and **warms the cache from that result** so the next read hits the fast path. This means a cache miss doesn't distinguish "genuinely unknown order" from "not consumed yet" until the fallback call resolves it — an acceptable trade-off for this project's scope, but worth knowing: under real load you'd want a bounded number of concurrent fallback calls, not one per waiting reader.

**Server-computed totals.** `POST /orders` never accepts `total` from the client — it's always `sum(quantity * unitPrice)`. This closes an obvious spoofing/drift vector and gives the validation layer something concrete to enforce, in miniature, the kind of input-trust discipline a real API needs.

**In-memory storage, both sides.** order-service's order store and bff-service's Kafka consumer group offsets both reset on restart (no persistent volume for either). This is a deliberate scope cut for a weekend/portfolio project, not an oversight — a real deployment would back order-service with Postgres and rely on Kafka's own offset durability rather than a fresh in-memory store each boot.

### Deliberately out of scope

- **Authentication/authorization** — no auth is implemented anywhere. Next step for a real deployment: OAuth2/JWT at the BFF edge, service-to-service auth (mTLS or a shared secret) between bff-service and order-service.
- **Multi-service event mesh** — one producer, one consumer is enough to demonstrate the pattern; a real polyglot backend would have several domain services publishing to (and consuming from) a shared event bus.
- **Frontend UI** — the BFF's REST API is the deliverable; nothing consumes it but curl/tests.
- **Production-grade Kafka** — single broker, single partition, no replication, `AllowAutoTopicCreation` left on. Fine for a local demo; a real deployment needs a proper partitioning strategy (probably keyed by customer or order ID for ordering guarantees), replication, and likely exactly-once semantics if downstream consumers can't tolerate duplicate processing.
- **Kubernetes manifests** — Docker Compose covers this project's scope.

## Running it

Requires Docker and the `docker compose` CLI plugin (`docker compose version`).

```sh
docker compose up --build
```

This builds and starts, in dependency order (health-check gated — Kafka needs to be genuinely ready before order-service starts, etc.): Kafka (single-broker KRaft), Redis, order-service, and bff-service.

Once it's up:

```sh
# Create an order
curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","items":[{"productId":"sku-1","quantity":2,"unitPrice":9.99}]}'
# → {"id":"<order-id>", ...}

# Read it back directly from order-service
curl http://localhost:8080/orders/<order-id>

# Read the frontend-shaped summary from the BFF (served from the Kafka-fed
# cache within a second or two of the event landing, not a proxy call)
curl http://localhost:8081/order-summary/<order-id>
```

Tear down with `docker compose down -v`.

### Running the tests

**order-service** (Go, no external dependencies needed):

```sh
cd order-service
go test ./...
```

**bff-service** (Kotlin/Gradle — the wrapper handles the Gradle/Kotlin toolchain itself; you need a JDK 21 on `PATH`, or run it via Docker as shown below):

```sh
cd bff-service
./gradlew test
```

This includes the integration test (`OrderSummaryKafkaIntegrationTest`) that publishes a fake `OrderCreated` event **directly to a real Kafka broker** (via Testcontainers, bypassing order-service entirely), waits for the BFF's actual Kafka consumer to process it, and asserts `GET /order-summary/{id}` reflects it — proving the event-driven boundary works, not just that individual functions return the right values. It needs Docker available to the test JVM.

If you don't have a local JDK, run the same command through the Gradle Docker image instead:

```sh
docker run --rm -v "$PWD":/home/gradle/project -w /home/gradle/project \
  -v /var/run/docker.sock:/var/run/docker.sock \
  gradle:9.7.1-jdk21-alpine ./gradlew test --no-daemon
```

This runs Gradle itself inside a container while Testcontainers (inside that same container) spins up sibling containers on your host's Docker daemon — a "Docker-outside-of-Docker" setup. If Testcontainers can't reach the containers it starts (a `ContainerLaunchException` on a port-wait), that's a known class of issue with this setup, not a problem with the test itself; add `--network host` (Linux) to the `docker run` command above, which puts the Gradle container on the host's network namespace directly and avoids the extra network hop.
