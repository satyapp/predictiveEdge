# PredictiveEdge Pre-Open Intraday Screener v0.1

| Field | Value |
|---|---|
| Status | Executable vertical slice with persisted pre-open evidence |
| Authority | Candidate-only advisory; no order or final recommendation authority |
| Ingestion endpoint | `POST /api/market-intelligence/v1/pre-open-snapshots` |
| Analysis endpoint | `POST /api/chart-intelligence/v1/pre-open-analyses/stored` |
| CSV workflow endpoint | `POST /api/chart-intelligence/v1/pre-open-csv-analyses` |
| Historical source | Tenant-owned finalized five-minute Market Intelligence bars |

## Purpose

The screener combines final exchange pre-open auction facts with causally available OHLCV history. It returns `BUY_CANDIDATE`, `SELL_CANDIDATE`, `WAIT`, `NO_TRADE`, or `INSUFFICIENT_EVIDENCE` for each instrument. A directional candidate still requires the documented first-fifteen-minute and session-VWAP confirmation. AI Decision Intelligence remains the sole authority for a final `BUY` or `SELL` recommendation.

## Initial policy

- Accept only `EQ` series instruments; trade-to-trade series fail closed.
- Require at least 60 finalized five-minute bars available at both supplied cutoffs.
- Require an absolute auction gap between 0.30% and 2.50% for continuation or failed-gap evaluation.
- Reject a pre-open bid/ask spread above 20 basis points.
- Require non-zero auction volume and at least INR 50 lakh of auction turnover.
- Calculate EMA20, EMA50, RSI14, ATR14, and auction volume relative to the median of the latest fifty finalized bars.
- Emit a gap-continuation candidate only when the auction direction, EMA structure, last close, and RSI range align.
- Emit a failed-gap-reversal candidate when the auction gap conflicts with the established trend.
- Return ATR-based indicative stop and 1R/2R targets. These levels are not executable until the confirmation rule completes.

## Causal behavior

Market Intelligence stores each final auction observation as immutable tenant/account-owned evidence with an evidence hash, exchange observation timestamp, and platform receipt timestamp. Its causal query requires both `observed_at <= analysisCutoff` and `received_at <= knowledgeCutoff`.

Chart Intelligence resolves that stored snapshot and reads history through `MarketBarQueryPort` with the same authenticated user, broker account, instrument subject, five-minute timeframe, analysis cutoff, and knowledge cutoff. The domain independently removes any bar ending or becoming available after the analysis cutoff. Evidence references preserve the bar interval, revision, and input-manifest hash.

## Snapshot ingestion

```json
{
  "brokerAccountId": "ZD123",
  "snapshots": [
    {
      "venue": "NSE",
      "symbol": "SBIN",
      "series": "EQ",
      "tradingDate": "2026-11-09",
      "previousClose": 1016.00,
      "auctionPrice": 1030.00,
      "auctionVolume": 573581,
      "auctionTurnover": 590788430.00,
      "bestBid": 1030.00,
      "bestAsk": 1030.40,
      "observedAt": "2026-11-09T03:39:30Z"
    }
  ]
}
```

The ingestion response returns the immutable snapshot ID, platform receipt time, and evidence hash.

## Stored-snapshot analysis

```json
{
  "brokerAccountId": "ZD123",
  "tradingDate": "2026-11-09",
  "analysisCutoff": "2026-11-09T03:40:00Z",
  "knowledgeCutoff": "2026-11-09T03:40:05Z",
  "historyDays": 90,
  "instruments": [
    { "venue": "NSE", "symbol": "SBIN" },
    { "venue": "NSE", "symbol": "INFY" }
  ]
}
```

The response declares `authority: CANDIDATE_ONLY`. Each instrument is either `ANALYZED`, with indicator values, gap, spread, auction participation, confirmation rule, indicative levels, reasons, and historical evidence references, or `SNAPSHOT_NOT_FOUND`. The original request-supplied snapshot endpoint remains available for compatibility and controlled replay.

## NSE CSV and Zerodha workflow

The multipart CSV endpoint accepts the standard NSE pre-open download, including quoted Indian-formatted numbers. It:

1. validates the required final-price, volume, value and bid/ask columns;
2. rejects incomplete rows with row-level diagnostics;
3. applies the governed gap, spread, auction-volume and turnover pre-filter;
4. ranks eligible rows by auction turnover and caps Zerodha calls at 25 instruments;
5. resolves every shortlisted symbol against the user's Zerodha instrument master, excluding anything that is not a cash equity;
6. persists the resolved final pre-open snapshot;
7. fetches up to 365 days of five-minute Zerodha candles using the user's authenticated broker connection, split into 90-day requests and merged by candle timestamp; and
8. returns all analyzed instruments plus the `BUY_CANDIDATE` and `SELL_CANDIDATE` subset.

Example multipart fields:

```text
brokerAccountId=ZD123
tradingDate=2026-11-09
observedAt=2026-11-09T03:39:30Z
analysisCutoff=2026-11-09T03:40:00Z
historyDays=365
maximumCandidates=10
file=@MW-Pre-Open-Market-ALL-09-Nov-2026.csv
```

The response includes parse rejections, per-symbol Zerodha failures, the number of historical bars obtained, complete assessments, and a candidate-only subset. Historical retrieval is read-only; this workflow cannot place an order or create the final BUY/SELL recommendation.

## Next increments

1. Re-evaluate candidates after the first finalized fifteen-minute bar and calculate the actual session VWAP trigger.
2. Publish the resulting immutable Chart resource into the AI Decision Intelligence input bundle.
3. Add benchmark and sector synchronization, ranking, risk review, and replay-based calibration.
