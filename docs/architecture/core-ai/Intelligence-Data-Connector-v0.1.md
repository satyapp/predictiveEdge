# Intelligence Data Connector v0.1

The connector separates nine evidence-acquisition domains from the twelve governed decision resources. External data never directly marks a decision resource ready; downstream normalizers must persist point-in-time snapshots, lineage, freshness and quality before the final decision bundle can use it.

## Evidence source policy

| Evidence domain | Primary/derived sources | Supplementary/optional sources |
|---|---|---|
| Market data | NSE, Zerodha | Trendlyne |
| Technical analysis | Raw-data technical engine | Trendlyne |
| Chart patterns | Pattern-detection engine | Raw-data engine |
| Fundamental analysis | Trendlyne | — |
| News and sentiment | NSE where available, Trendlyne | Yahoo Finance (inactive pending a supported API) |
| Corporate actions | NSE | Trendlyne |
| Macro and economic | NSE, Zerodha | Trendlyne where available |
| Institutional flows | NSE | Trendlyne |
| Historical and predictive | Zerodha | Trendlyne |

NSE and Trendlyne capabilities are checked explicitly. A configured provider that does not expose a requested domain returns `UNSUPPORTED`; a missing connector or credential returns `UNAVAILABLE`; transport failures return `FAILED`. There is no silent fallback or source substitution.

CoinDesk is excluded because the platform's current scope is NSE equities rather than crypto assets. Yahoo Finance is retained in the requested source plan, but no connector is activated because Yahoo does not publish a supported Finance API. Scraping or reverse-engineered endpoints are outside this design.

## Twelve-resource fan-out

`IntelligenceSourcePlan` maps each `DecisionResourceType` to the evidence domains it consumes. Market, Chart, Scanner, Strategy, Risk, Portfolio, Learning, Data Quality, Regime/Drift, Execution, Validation and Calibration are all covered. Internal account state, risk policy, learning outcomes, execution costs, model validation and probability calibration remain authoritative internal inputs; external feeds only enrich their evidence.

The raw-data technical engine and chart-pattern engine implement `DerivedEvidencePort`. This prevents provider calculations from replacing deterministic, versioned local features.
