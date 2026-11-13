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
   - calls local Ollama through `AiRecommendationGateway` for a first-level proposal;
   - requires `GovernedFinalDecisionIntelligence` to execute over the complete
     bundle and reject or approve that proposal; and
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

- automated broker execution;
- a UI/API for starting evaluations;
- multi-user or multi-equity configuration; and
- automatic promotion from shadow to manual/live use.

## AI provider environments

The first-level analysis boundary supports structured model proposals behind the
`AiRecommendationGateway` contract. Local Ollama is the required default:

- Default: `PE_AI_PROVIDER=ollama`, using the local Ollama `/api/chat` endpoint
  and configured local model (currently `qwen3:8b`).
- Final provenance gate: `PE_SHADOW_FIRST_LEVEL_PROVIDER=ollama`.
- Disabled: `PE_AI_PROVIDER=none` explicitly disables first-level analysis.
- The OpenAI adapter remains available for controlled experiments, but its
  proposal is rejected by the final gate while Ollama provenance is required.

There is no automatic provider fallback. An Ollama failure cannot silently send
personal trading evidence elsewhere. Provider, model, prompt, evidence hashes
and output remain part of the decision audit trail. After Ollama returns, the
mandatory `GovernedFinalDecisionIntelligence` gate executes over all twelve
resources. A proposal is never recorded as an approved BUY/SELL when that final
gate is absent, returns no result, rejects provenance, or finds a failed policy.

Before either provider is called, the gateway resolves the exact immutable JSON
payload for all twelve mandatory evidence resources. Each payload must match its
user, venue, instrument, resource type, payload reference, evidence hash and
causal availability time. Missing, mismatched or invalid JSON fails closed.
References alone are never treated as model evidence, and the gateway performs no
silent truncation, sampling or omission. If a configured model cannot accept the
complete governed payload within its external context limit, the decision is
blocked instead of dropping evidence.

The Ollama context window, structured-output allowance and inference timeout are
explicit environment settings. Output is bounded because the recommendation
schema is compact and unbounded generation is unsafe; input evidence is not
trimmed to meet that output bound. A deployment must size the selected local
model's context window for the complete governed input.

The current local Windows/Vulkan profile is verified at an 8,192-token context.
The previous 32,768-token default exhausted pinned memory on this machine, so
the application now defaults to `PE_OLLAMA_CONTEXT_WINDOW_TOKENS=8192`. Evidence
that does not fit must block the evaluation; it must not be silently truncated.

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

Zerodha FULL equity packets now preserve all five bid and five ask levels as
provider-normalized, ordered depth entries on the immutable equity tick. Quantity
is decoded as unsigned 32-bit data and order count as unsigned 16-bit data.
Admitted, de-duplicated equity ticks now append their five-by-five book to
`market_intelligence.market_depth_snapshot`. Each row is tenant/account scoped,
SHA-256 hashed and selectable only when its receipt time is at or before the
decision knowledge cutoff. This closes the governed depth-capture gap; a
direction-aware fill and slippage policy is still required before Execution can
be produced directly from the live stream.

The Zerodha adapter also implements a deliberately read-only account snapshot
provider over `GET /user/margins`, `GET /portfolio/positions` and
`GET /portfolio/holdings`. The three responses are normalized as funds, net/day
positions and holdings, captured as a bounded observation window, and append-
stored in `broker_evidence.account_snapshot` with tenant/account identity and a
deterministic evidence hash. The adapter exposes no order placement, position
conversion or holdings-authorisation operation.

When Market, Chart, Risk, Portfolio or Execution selects a factual snapshot for
a causal decision batch, the exact snapshot JSON is append-published using the
same user, instrument, resource type, payload reference, evidence hash and
availability time as its `DecisionResource`. Identical publication is idempotent;
a conflicting JSON payload for the same identity fails closed.

Execution, Risk and Portfolio producers now consume one explicit advisory
assessment request containing trader direction, quantity and stop-loss price.
Execution walks asks for BUY and bids for SELL, calculates visible entry/exit
capacity, volume-weighted fill, spread, impact, slippage and configured fees.
Risk calculates proposed stop-loss risk and daily P&L capacity from current
broker facts. It does not infer stop risk for existing open positions: if an open
position exists without governed stop evidence, Risk returns `UNAVAILABLE` and
`VETO`. Portfolio calculates present exposure plus projected post-trade exposure,
weight and short-position policy. Missing, stale or contradictory facts produce
persisted `UNAVAILABLE`/`INVALID` snapshots and `VETO`.

The producer is opt-in through `PE_SAFETY_ASSESSMENT_ENABLED=true`. Monetary and
concentration thresholds have no defaults and must be explicitly supplied:

- `PE_EXECUTION_MAXIMUM_SPREAD_BPS`, `PE_EXECUTION_MAXIMUM_SLIPPAGE_BPS`,
  `PE_EXECUTION_FEES_AND_TAXES_BPS`;
- `PE_RISK_POLICY_VERSION`, `PE_MAXIMUM_RISK_PER_TRADE`, `PE_DAILY_LOSS_LIMIT`,
  `PE_MAXIMUM_OPEN_RISK`, `PE_MAXIMUM_POSITION_VALUE`; and
- `PE_PORTFOLIO_POLICY_VERSION`, `PE_MAXIMUM_SINGLE_POSITION_WEIGHT_PERCENT`
  and `PE_ALLOW_SHORT_POSITIONS`.

After a read-only broker account capture and live FULL-depth subscription, an
authenticated caller can append the three assessments through
`POST /api/decision/v1/safety-assessments`. This endpoint is advisory-only and
contains no order operation.

The next bounded task is to bind this producer to the single-scope shadow intent
lifecycle, then implement Scanner and Strategy contributors. Learning, Data
Quality, Regime/Drift, Validation and Calibration follow the same contributor and
exact-payload contracts. Only after a complete twelve-resource batch can be
produced and replayed should the shadow scheduler invoke either structured AI
provider.
