# Intraday Opportunity Engine v0.1

## Scope

The engine scans only the official Nifty 500 constituent universe and remains research-only. It supports long and short watch candidates and never places orders.

## Reused processing path

The engine does not introduce a second recommendation backend. It changes intake and deterministic discovery only:

1. Download and cache the official Nifty 500 constituent snapshot.
2. Read NSE CM Market MCP equity price-watch observations and retain `EQ` constituents only.
3. Build the shared `MarketScreenDataCopy` canonical intake object.
4. Pass that copy to `MarketScreenHistoricalAnalysisService`.
5. Reuse Zerodha instrument resolution and finalized five-minute history.
6. Reuse the governed EMA, RSI, ATR, indicative-risk and Ollama/golden-copy processing.

Both the source timestamp and fetch timestamp are retained. The application does not silently substitute end-of-day data for a live request.

## Extensibility boundary

`INTRADAY_OPPORTUNITY` is a versioned screen type. Future deterministic features—same-time relative volume, VWAP, spread/depth, Nifty 500 relative strength, sector context, opening-range setup detection and catalysts—belong before canonical shortlisting or as additional evidence fields. They must not be calculated by the LLM.

The page displays the target six-factor, 100-point strategy profile. Until each evidence source is available, its contribution is treated as an uncalibrated ranking input rather than a confidence probability. Hard eligibility, freshness and execution-quality gates take precedence over the score.

## Runtime dependencies

- Official Nifty Indices constituent CSV
- NSE CM Market MCP `cm_get_equity_stocks`
- Connected Zerodha account for instrument identity and historical candles
- Existing Ollama adapter for structured explanation and challenge

