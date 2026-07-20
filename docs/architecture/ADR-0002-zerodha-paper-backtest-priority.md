# ADR-0002: Zerodha Data, Paper Trading, and Backtesting Before Live Trading

- Status: Accepted
- Date: 2026-07-16

## Context

The first named broker integration is Zerodha. PredictiveEdge must validate strategies and execution behavior without exposing users to premature live-order risk. Zerodha's Kite Connect API separates login, historical market data, and order operations, allowing the platform to establish the data and simulation path first.

## Decision

Implementation priority is:

1. Zerodha v3 backend login and request-token exchange.
2. Zerodha historical candle ingestion through the broker-neutral market-data SPI.
3. Paper Trading execution and deterministic strategy backtesting.
4. Persistent backtest runs, metrics, costs, slippage, and audit evidence.
5. Live trading only after explicit execution-gateway controls and a separate architecture decision.

The Zerodha module does not implement `BrokerAdapter` and therefore cannot place orders. It implements only historical market data and login support. Backtests replay chronological candles and route every simulated order through `PaperBrokerAdapter`.

## Guardrails

- Zerodha API secrets and access tokens remain backend-only and are redacted from value-object string output.
- The current implementation does not persist credentials; encrypted credential storage is a later infrastructure slice.
- Credential infrastructure must treat `403 TokenException` as session invalidation and account for normal access-token expiry at 6:00 AM the next day.
- Historical-data ingestion must enforce Zerodha's 3 requests/second limit and use caching or batching for backtest datasets.
- Candle ordering is validated to reduce accidental look-ahead.
- Paper fills currently use candle close prices and do not yet model spread, slippage, fees, taxes, liquidity, or corporate actions.
- No live-order controller, service, or Zerodha order transport is included.

## Official API References

- Official Java SDK repository (compatibility baseline: 4.0.0, released 2026-03-30): https://github.com/zerodha/javakiteconnect/tree/master
- Login and token exchange: https://kite.trade/docs/connect/v3/user/
- Historical candles: https://kite.trade/docs/connect/v3/historical/
- Live-order lifecycle caveats: https://kite.trade/docs/connect/v3/orders/

The HTTP API documentation defines the wire contract. The official Java repository is checked for current SDK behavior, models, breaking changes, and release updates before changing the Zerodha adapter. Version 4.0.0 retains the v3 login and historical-data contracts used here; its breaking changes concern live order responses and auto-slice handling, which remain outside this phase.

## Consequences

The first useful end-to-end workflow is Zerodha data to a Paper Trading backtest result. Live trading remains technically unavailable until safety, audit, persistence, and explicit user-control requirements are designed and tested.
