# ADR-0006: FYERS Java SDK and User-Confirmed Order Placement

- Status: Proposed
- Date: 2026-11-22
- Supersedes in part: ADR-0004 (recommendation-only trading), for FYERS cash equities only
- Related: ADR-0005 (FYERS Automate and FIA review loop)

## Context

The owner decided to adopt the official FYERS Java SDK in full, including order placement. Until now
PredictiveEdge had no broker write path (ADR-0004) and read FYERS data through its own HTTP client.

Facts about the SDK (v2.2.0, checked 2026-11-22): it is distributed only as an obfuscated Java 8 fat jar in
GitHub, with no published licence; it exposes a single process-wide `FyersClass` with public `clientId` and
`accessToken` fields; and it bundles unrelocated libraries including log4j 1.2. Details are in
`backend/predictiveedge-parent/third-party/README.md`.

## Decision

1. The SDK is added to `fyers-adapter` from a repo-local Maven repository with checksum verification.
2. `FyersSdkGateway` is the only class that touches `FyersClass`. Each call takes a fair lock, installs the
   calling user's own credentials, runs, and clears them. It covers account reads (profile, funds, holdings,
   positions, orders, trades), market data (quotes, depth, history, option chain, market status) and screeners.
   The existing `FyersHttpClient` stays in use for OAuth and current screens; callers can move to the gateway
   one by one.
3. `FyersOrderService` can place and cancel orders, with these guardrails:
   - Off by default: `predictiveedge.fyers.orders.enabled=false` (`PE_FYERS_ORDERS_ENABLED`).
   - Every order must carry the user's explicit confirmation of the exact symbol, side and quantity.
   - NSE/BSE cash equities only; limit and stop-limit orders only; caps on quantity (default 100) and order
     value (default ₹1,00,000).
   - Every request and result is logged with the user id.
   - No automatic caller: AI, the stock pipeline, Trade Guardian and FYERS Automate never place orders.
4. No order API or UI is exposed yet. Those wait for the UI wireframe sign-off.

## Consequences

- The user can later place an order from PredictiveEdge, but only by confirming it themselves.
- FYERS API terms (section 6) forbid fully automated trading without exchange approval. Keeping a human
  confirmation on every order is what keeps this within those terms.
- SDK calls are serialised across all users. That is acceptable at current volume; if it becomes a bottleneck,
  move hot read paths back to `FyersHttpClient`, which is per-request.
- The bundled log4j 1.2 and the missing licence are open risks to close with FYERS before any distribution.
