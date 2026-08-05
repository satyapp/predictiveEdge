# Zerodha Upstream Compatibility

## Sources

Use both official sources when changing this adapter:

1. Kite Connect HTTP API documentation: https://kite.trade/docs/connect/v3/
2. Official Java client repository: https://github.com/zerodha/javakiteconnect/tree/master

The HTTP documentation is authoritative for the wire protocol. The Java repository is the reference for current Java-facing behavior, model changes, and migration notes.

## Current Baseline

- Audited: 2026-08-05
- Official Java client: 4.0.0
- Release date: 2026-03-30
- HTTP header version: 3

The current adapter matches the upstream Java client for:

- Login URL parameters: `api_key` and `v=3`
- Session checksum: SHA-256 of `apiKey + requestToken + apiSecret`
- Session logout: `DELETE /session/token` with `api_key` and `access_token`
- Session exchange endpoint and form fields
- Historical instrument token and interval path
- Historical `from`, `to`, `continuous`, and `oi` parameters
- Candle fields: timestamp, OHLC, volume, and optional open interest
- WebSocket packet framing, heartbeat, full equity packet, and full index packet layouts

## Operational Requirements From the HTTP API

- A normal access token expires at 6:00 AM on the following day unless it is invalidated earlier.
- A `403` response with `TokenException` requires clearing the stored session and restarting login.
- Historical candle requests are limited to 3 requests per second.
- Quote requests are limited to 1 request per second; most other non-order endpoints are limited to 10 requests per second.
- The API secret and access token must remain backend-only.

Session expiry handling, encrypted token persistence, request throttling, retry policy, and historical-data caching must be implemented before the adapter is exposed through application APIs.

Live-stream exposure additionally requires bounded reconnect/backoff, fragmented-frame handling, subscription replay,
heartbeat/staleness monitoring, daily instrument-master refresh, and deterministic historical gap backfill.

## Intentional Differences

- PredictiveEdge uses broker-neutral domain records rather than exposing Zerodha SDK models.
- Historical query timestamps are explicitly formatted in `Asia/Kolkata`; the SDK uses the JVM default timezone.
- Full-mode packets are decoded directly from the documented big-endian wire protocol and mapped back to stable instrument identities.
- Heartbeats are ignored; malformed, truncated, and unsubscribed packets fail closed.
- Credentials are supplied through a session provider so storage and encryption remain infrastructure concerns.
- The Zerodha module has no live-order implementation.

## Upgrade Checklist

Before adopting an upstream release:

1. Review the repository README breaking-change section and latest release.
2. Compare login, session exchange, historical data, instrument, and candle source models.
3. Check interval constants, routes, headers, rate limits, error types, and token-expiry behavior against HTTP docs.
4. Update adapter contract fixtures and run the complete platform reactor.
5. Record the new baseline here and in ADR-0002.

Version 4.0.0 changes `placeOrder` to return `OrderResponse` and folds auto-slice into normal order placement. These changes are tracked for the future live-trading phase and do not affect the current historical-data and backtest path.
