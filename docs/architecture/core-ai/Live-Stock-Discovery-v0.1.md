# Live Stock Discovery v0.1

## Decision

Live stock discovery changes only the market-screen intake adapter. It does not create a second ranking,
historical-enrichment, AI, recommendation, persistence, or risk-processing flow.

```text
NSE CSV -> CSV parser -----+
                           +-> MarketScreenDataCopy -> existing deterministic ranker
NSE MCP -> MCP normalizer -+                         -> existing Zerodha enrichment
                                                     -> existing technical analyzer
                                                     -> existing Stage-1 / golden copy
                                                     -> existing history persistence
```

`MarketScreenDataCopy` is the convergence contract. Every source must supply normalized mainboard-equity
observations, source and observation timestamps, freshness, and row-level rejections before downstream processing.

## Live intake rules

- Discover the connected CM Market MCP tool inventory at runtime.
- Enable only categories backed by an advertised NSE MCP tool.
- Never replace an unavailable live category with a partial equity list, website scrape, or EOD dataset.
- Enrich screen rows with `cm_get_stock_quote` when the screen payload lacks canonical OHLC fields.
- Accept only NSE `EQ` series rows; reject rights entitlements, debt, SME, and other instruments.
- Preserve the provider tool name and timestamps in the final data copy.
- Report freshness as `UNKNOWN` when the provider does not publish an observation timestamp.

## API

- `GET /api/chart-intelligence/v1/live-stock-discovery/capabilities`
- `POST /api/chart-intelligence/v1/live-stock-discovery/ollama`

Enable the adapter with `PE_NSE_MCP_ENABLED=true`. The official endpoints remain configurable through
`PE_NSE_MCP_CM_MARKET_URL` and `PE_NSE_MCP_BHAVCOPY_URL`.

## Current provider boundary

The page displays all five approved discovery categories, but capability discovery is authoritative. At the time
of implementation the connected CM Market MCP advertises top-gainer and top-loser tools. The other category cards
remain visible and unavailable until the official server advertises corresponding tools; PredictiveEdge does not
manufacture those rankings from incomplete inputs.
