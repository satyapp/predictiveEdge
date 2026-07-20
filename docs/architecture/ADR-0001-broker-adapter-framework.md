# ADR-0001: Broker Adapter Framework Boundary

- Status: Accepted
- Date: 2026-07-16

## Context

PredictiveEdge must support multiple brokers without allowing broker-specific APIs, credentials, or order semantics to leak into the platform core. The system architecture also requires every trade to pass through a future Trade Execution Gateway before it reaches a broker.

## Decision

Broker integration begins with three modules:

- `broker-domain` owns broker-neutral account, instrument, capability, and order concepts.
- `broker-spi` is the small interface implemented by broker plugins.
- `broker-paper-adapter` is the first plugin and provides deterministic, in-memory market-order execution for development and contract testing.

The SPI accepts an opaque credential reference rather than raw credentials. A future broker-connection service will resolve encrypted credentials inside infrastructure and construct authenticated real-broker adapters. The current paper adapter does not need credentials.

Client-generated order IDs are mandatory and repeated placement is idempotent. This creates the duplicate-execution seam required by the Trade Execution Gateway.

The initial SPI deliberately contains only account snapshots, market-order placement, and order lookup. Capabilities advertise optional behavior so later adapters can add limit orders and cancellation without broker checks in core code.

## Alternatives Considered

- Start directly with FYERS or Zerodha. Rejected because the first implementation would define core contracts around one vendor's authentication and payloads.
- Put broker code in the existing infrastructure module. Rejected because plugins need a narrow SDK dependency and independently testable lifecycle.
- Implement the full execution gateway now. Deferred until risk limits, audit persistence, and user execution preferences have explicit designs.

## Consequences

- A real broker adapter can depend only on `broker-spi` and map vendor payloads at its boundary.
- Paper trading gives the platform a safe integration target before real-money execution is enabled.
- In-memory paper state is intentionally non-durable and is not production-ready.
- OAuth connection, encrypted credential persistence, order audit storage, and the Trade Execution Gateway remain required before any live-trading endpoint is exposed.
