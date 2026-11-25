# Stock Stage-One Evidence Contract v0.1

## Decision

Before a selected stock is sent to Ollama, PredictiveEdge builds a point-in-time evidence envelope for the requested trading type. FYERS is the primary stock-discovery and broker market-data provider. Zerodha remains a supported broker adapter and fallback. Trendlyne is supplementary and is used only for capabilities its configured API actually returns.

Unavailable evidence is recorded with provider, status, timestamp and reason. It is never invented. A later completeness policy decides whether the stock is eligible for Stage-1 AI.

## Recommendation profile

Every stock request identifies one of these profiles.

| Profile | Holding policy | Historical data | Primary indicator set |
|---|---|---|---|
| `INTRADAY` | Same trading day; mandatory exit policy at 15:15 Asia/Kolkata | Current-session 1-minute/5-minute bars; at least 60 prior trading days of 5-minute bars; daily regime context | Session VWAP, EMA 9/20/50, RSI 14, MACD, ATR 14, ADX 14, Bollinger 20/2, relative volume, OBV, ROC, opening range, support/resistance |
| `SHORT_TERM_POSITIONAL` | Multi-day to multi-week | 30/60-minute bars for at least 6 months and daily bars for at least 2 years | EMA 20/50/200, RSI 14, MACD, ADX 14, ATR 14, Bollinger 20/2, OBV, relative strength, swing support/resistance, gap/event risk |
| `LONG_TERM_POSITIONAL` | Multi-month or longer | Daily bars for at least 5 years and weekly bars when the subscribed provider plan makes them available | SMA/EMA 50/100/200, weekly trend, RSI, MACD, ADX, ATR/NATR, long-term relative strength, drawdown, volatility and volume accumulation |

The requested profile, horizon, analysis cutoff, market timezone and applicable square-off policy are part of the AI request. A positional recommendation must never inherit the intraday 15:15 exit rule.

## Evidence collected for each stock

| Evidence group | Required details | Preferred provider |
|---|---|---|
| Identity and request | NSE symbol, provider symbol/token, trading profile, recommendation horizon, analysis cutoff, user/broker account reference, source screener and rank | PredictiveEdge canonical request |
| Discovery | FYERS screener identifier/configuration, universe, timeframe, source rank, selection reason | FYERS primary |
| Live quote | LTP, absolute/% change, open/high/low/previous close, volume, average traded price, last trade time, bid, ask and spread | FYERS primary; Zerodha fallback |
| Market depth | Five bid/ask levels, price, quantity and order count, total buy/sell quantity, imbalance, spread, circuit limits, volume and LTQ when returned | FYERS primary; Zerodha fallback |
| Historical OHLCV | Profile-specific bars, interval, window, volume, optional OI, missing-bar checks and provider range limits | FYERS primary; Zerodha fallback |
| Technical indicators | Profile-specific indicators listed above, trend/momentum/volatility/volume groups, indicator parameters, warm-up status and calculation timestamp | PredictiveEdge derived from FYERS candles; Trendlyne cross-check only |
| Chart structure | Opening range, VWAP relationship, gaps, breakout/breakdown, support/resistance, swing structure and detected patterns | PredictiveEdge derived engine |
| Pre-open analysis | Market status, indicative/pre-open quote when exposed, gap versus previous close, pre-open volume/imbalance where available, and availability status | FYERS current market state; NSE exchange evidence where required |
| Fundamentals and SWOT | Financial quality, growth, valuation, leverage, profitability and explicit strengths/weaknesses/opportunities/threats | Trendlyne only when its API returns verified fields |
| News | Headline, source, publication time, stock relevance, event category and URL/reference | Trendlyne/NSE where supported |
| Sentiment | Per-item sentiment, aggregate score, confidence, sample count, lookback window and disagreement | PredictiveEdge derived from retrieved news; not inferred from price alone |
| Corporate actions | Results, dividends, splits, bonuses, rights, board meetings and other announced actions with ex/record dates | NSE authoritative; Trendlyne supplementary |
| Market and sector context | NIFTY/sector return, breadth, volatility regime, market status and relative strength | FYERS/NSE |
| Ownership/flows | Institutional/insider/shareholding evidence when supported | Trendlyne supplementary; NSE where supported |
| Data quality and provenance | Provider, endpoint/tool, fetched-at, observed-at, cutoff, freshness, status, failure reason, payload hash and evidence version | Required for every evidence item |

## Provider policy

1. FYERS is priority 1 for discovery, quotes, historical candles, five-level market depth and market status.
2. Zerodha remains a supported adapter and priority 2 for broker quote/history/depth capabilities. It is not removed.
3. Trendlyne is never used as the primary broker market-data source. It is supplementary for fundamentals/SWOT, technical cross-checks, news, corporate events and ownership only when the configured MCP/API tool returns that capability.
4. NSE evidence remains authoritative for exchange announcements and corporate actions where available.
5. Provider failure is recorded explicitly. The system does not silently label fallback data as FYERS data.

## Current implementation status

| Area | Status |
|---|---|
| FYERS screener discovery and quotes | Implemented |
| FYERS as primary instrument resolver and historical-candle provider | Implemented when FYERS is enabled |
| FYERS quote, market-depth and market-status evidence in the Stage-1 collector | Implemented |
| Zerodha adapter retained | Implemented |
| Trendlyne supplementary connector | Implemented, conditional on configured API/MCP capability |
| Deterministic technicals | Partial: EMA 20/50, RSI 14 and ATR 14 are currently calculated |
| Full profile-specific indicator sets | Not yet implemented |
| Dedicated news-sentiment scoring | Not yet implemented; current connector retrieves news evidence only |
| Complete SWOT normalization | Partial and provider-dependent |
| Profile selector for intraday/short-term/long-term | Not yet implemented |
| Persist-before-AI request/response audit trail | Separate approved requirement; not included in this provider/evidence slice |

## External capability references

- FYERS data APIs expose quotes, historical candles, depth and market status: <https://myapi.fyers.in/docsv3>
- FYERS depth includes bid/ask levels and can include OHLCV: <https://support.fyers.in/portal/en/kb/articles/how-do-i-access-market-depth-data-for-a-specific-symbol-using-the-market-depth-api>
- Zerodha supports historical candles and complete quotes including depth: <https://kite.trade/docs/connect/v3/historical/> and <https://kite.trade/docs/connect/v3/market-quotes/>
