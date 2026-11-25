# FYERS-first Intraday Screener v0.1

## Decision

PredictiveEdge will use FYERS API v3 as the primary intraday discovery and broker market-data provider for the Nifty 500 universe. Zerodha remains a supported broker adapter. The existing NSE MCP adapter remains intact as an exchange-data resilience source; it is not removed, silently substituted, or treated as guaranteed live data. Trendlyne is supplementary and is used only for capabilities verified through its configured API/MCP tools.

The per-stock payload required before Stage-1 AI is defined in
[`Stock-Stage-One-Evidence-Contract-v0.1.md`](./Stock-Stage-One-Evidence-Contract-v0.1.md).

Only the intake path changes. Every accepted provider payload must be normalized into the existing `MarketScreenDataCopy` before deterministic ranking, historical analysis, Ollama analysis, golden-copy persistence, and optional second-level analysis run.

## Page contract

- One **Intraday Screener** workspace replaces the overlapping Live Discovery and Intraday Engine navigation.
- Zerodha, FYERS, NSE and Trendlyne are presented as compact connection toggles. Paper Trading and Backtesting are intentionally absent until those workflows are requested.
- Supported categories: Most Traded, Intraday Surges, Intraday Falls, Gap Up, Gap Down, 52W High, 52W Low, Unusual Volume, Breakouts, and Breakdowns.
- Each category displays zero, one, or two qualified candidates.
- Consolidated Intraday Opportunities displays zero to five unique candidates.
- A stock with multiple directionally aligned categories receives a versioned confluence advantage. Conflicting signals are flagged or excluded, and confluence never overrides liquidity, freshness, execution, or risk vetoes.
- No result is fabricated to fill either limit.

## Interaction and analysis lifecycle

1. The user selects **Explore by setup**, Nifty 500 universe, timeframe, and recommendation limit.
2. **Get qualified stocks** requires an active FYERS connection. NSE MCP is never substituted silently.
3. Every qualified stock runs the existing backend intelligence modules plus level-one AI. Its canonical evidence and completed analysis run are persisted using the existing analysis-run recorder.
4. The user explicitly promotes a qualified stock into **Consolidated Intraday Opportunities**.
5. Promotion triggers a fresh consolidated run of the backend intelligence modules and level-one AI. Level-two AI is added to this boundary when enabled.
6. Until level-two AI is enabled, the refreshed level-one agent remains the final consolidated decision authority.

## Provider boundary

```text
FYERS Configuration API -> exact screener IDs and supported parameters
FYERS Screener / Quote APIs -> discovery observations
                                  \
NSE MCP (fallback, readiness-gated) -> provider-neutral intake
                                      -> MarketScreenDataCopy
                                      -> existing PredictiveEdge analysis flow
```

The FYERS adapter must discover screener IDs through `GET /api/v3/screeners/config`. It must not assume that display names are stable API identifiers. Standard requests use `GET /api/v3/screeners/standard` with only parameters advertised by the selected screener configuration.

Runtime secrets (`client_id`, access token, refresh material, and app secret) must never be committed, logged, returned to the browser, or stored in the canonical evidence copy.

## Delivery slices

1. Consolidate and redesign the page, including honest provider states and hard display limits.
2. Add FYERS OAuth/app connection and encrypted credential lifecycle.
3. Cache the authenticated Configuration API response and map the ten product categories to verified FYERS IDs.
4. Normalize FYERS rows into `MarketScreenDataCopy`; retain source timestamps and rejection reasons.
5. Scan all categories, retain full internal evidence, select top two per category, deduplicate, apply directional confluence, and select the consolidated top five.
6. Add streaming quotes/candles for freshness and revalidation without duplicating broker fetches.

## Non-goals for v0.1

- No automatic order execution.
- No promise of profitability or calibrated win probability.
- No hard-coded FYERS screener IDs without an authenticated Configuration API response.
- No use of NSE MCP availability as evidence that its cache contains live rows.
