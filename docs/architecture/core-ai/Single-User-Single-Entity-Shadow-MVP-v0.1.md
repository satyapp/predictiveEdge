# Single-User / Single-Entity Shadow MVP v0.1

## Fixed boundary

This implementation deliberately supports:

- exactly one configured user;
- exactly one configured NSE equity;
- advisory AI recommendations only;
- shadow observation and strict model scoring;
- no broker-order creation, modification or cancellation; and
- no multi-user, multi-equity, community or public workflow.

The database contains a singleton scope guard. After the first evidence batch or
decision is stored, a different user/equity combination cannot satisfy the
foreign-key boundary.

## Implemented slice

1. `ShadowEvidenceBatch` accepts one immutable handoff containing:
   - all twelve required decision resources;
   - `PointInTimeEvidenceManifest`;
   - `ExecutionContext`; and
   - one fixed `ShadowScope`.
2. `JdbcShadowEvidenceStore` appends the batch and selects the latest batch that
   was known and still valid at the decision cutoff.
3. `ShadowDecisionService`:
   - enforces the configured user/equity;
   - refuses inactive Trader Intent;
   - prevents an AI call when input is incomplete or stale;
   - calls `AiRecommendationGateway` as the sole recommendation origin;
   - applies a reject-only policy validator; and
   - appends the complete input, raw structured AI result and validation result.
4. Directional recommendations require:
   - all resources ready and causal;
   - Risk, Portfolio, Validation and Execution gates equal to `PASS`;
   - positive expected value after costs;
   - calibrated probability at or above policy; and
   - an entry window covered by current execution evidence.
5. `ShadowOutcomeService` resolves each actionable recommendation as exactly
   `WIN` or `LOSS`. Breakeven, invalid entry and stop-first cases are losses.
6. `ShadowEvidenceBatchService` captures one causal batch through exactly one
   contributor for each of the twelve mandatory resource types. Missing or
   duplicate contributors fail configuration; an unavailable source must be
   represented explicitly and therefore blocks the later AI call.
7. Initial source adapters now translate:
   - the governed semantic `MarketContextSnapshot` into `MARKET`; and
   - the immutable `ChartSnapshot` into `CHART`.

   A raw market bar is deliberately not treated as complete Market
   Intelligence. It remains upstream evidence for Market Context, which is
   responsible for the broader market, news, corporate, sector and policy
   dimensions defined by the architecture.

## Persistence

Flyway migration `V011__single_scope_shadow_decisions.sql` creates:

- `decision.shadow_scope`;
- `decision.shadow_evidence_batch`;
- `decision.shadow_decision_case`; and
- `decision.shadow_model_outcome`.

All decision evidence is inserted append-only. There is no update path in the
shadow repositories.

## Runtime activation

Shadow runtime is disabled by default and also requires an
`AiRecommendationGateway` bean. Both conditions prevent accidental activation.

```text
PE_SHADOW_DECISION_ENABLED=true
PE_SHADOW_USER_ID=<personal-user-uuid>
PE_SHADOW_VENUE=NSE
PE_SHADOW_INSTRUMENT_ID=<single-equity-instrument-id>
PE_SHADOW_MARKET_CONTEXT_HORIZON=INTRADAY
PE_SHADOW_MINIMUM_DIRECTIONAL_PROBABILITY=0.55
```

Supplying these values does not enable broker execution. The shadow service has
no broker-write port.

## Intentionally not implemented in this slice

- a production AI provider adapter;
- automated broker execution;
- a UI/API for starting evaluations;
- multi-user or multi-equity configuration; and
- automatic promotion from shadow to manual/live use.

`MarketContextSnapshot` and `ChartSnapshot` now have tenant-owned, append-only
PostgreSQL stores. Their queries enforce analysis, knowledge and availability
cutoffs in SQL, retain expired snapshots for replay, and allow the decision
resource adapters to distinguish stale evidence from missing evidence.

Execution, Risk and Portfolio now have factual point-in-time contracts and
tenant-owned, append-only PostgreSQL stores. Risk and Portfolio must explicitly
return `PASS` or `VETO`; missing evidence is represented as `UNAVAILABLE` plus
`VETO`. A pass cannot contradict exhausted risk capacity or a known portfolio
concentration breach. Execution derives its gate only from recorded entry and
exit feasibility. None of these resources creates a trade direction.

The next bounded task is to populate these snapshots from broker account and
market-depth observations. The Zerodha full-packet decoder currently ignores
its ten depth entries, so bid/ask depth must be normalized before Execution can
be considered factual. Scanner, Strategy, Learning, Data Quality, Regime/Drift,
Validation and Calibration then follow the same contributor contract. Only
after a complete batch can be produced and replayed should a structured AI
provider adapter be implemented behind `AiRecommendationGateway`.
