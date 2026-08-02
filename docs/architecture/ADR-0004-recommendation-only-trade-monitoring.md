# ADR-0004: Recommendation-Only Trading and Human-Executed Orders

- Status: Accepted
- Date: 2026-07-31
- Supersedes: The paper-before-live execution sequence in ADR-0002

## Context

PredictiveEdge is a trading-intelligence platform whose constitutional boundary is
"AI recommends. The trader decides." Building a realistic paper exchange would
require substantial work in order-book behavior, liquidity, latency, slippage,
fees, and venue-specific rules. Connecting the platform to live order-placement
APIs would create financial risk and move the product beyond decision support.

The platform still needs to monitor a trade after a trader places it manually.
That monitoring must retain the original recommendation evidence, observe later
market and position facts, and provide timely, explainable guidance without
controlling the broker account.

## Decision

PredictiveEdge will provide recommendation-only trading intelligence.

1. The platform may create a `TradeRecommendation`, `TradePlan`, and
   `ProposedOrder` for trader review.
2. The trader places, modifies, and closes every order outside PredictiveEdge.
3. PredictiveEdge will not implement a live-order adapter, Trade Execution
   Gateway, or broker write endpoint.
4. A trader may register an actual fill manually and link it to the originating
   Trade Plan.
5. A future broker observation adapter may import orders, trades, and positions
   read-only. It must not expose order-placement, modification, or cancellation.
6. Trade Guardian may monitor a registered trade and publish advisory health,
   reassessment, and exit-review events. It must never take broker action.
7. `PaperBrokerAdapter` remains an internal deterministic test utility. It will
   not become a product exchange simulator or a prerequisite for real-market
   decision support.

## Product boundary

```text
Live market and read-only broker data
                 |
                 v
       Explainable recommendation
                 |
                 v
        Proposed order / Trade Plan
                 |
                 v
       Trader executes at the broker
                 |
                 v
 Manual registration or read-only observation
                 |
                 v
         Trade Guardian advisories
                 |
                 v
          Trader decides and acts
```

No path from Decision Intelligence or Trade Guardian may reach a broker write
operation.

## Required controls

- Proposed orders are clearly labelled as suggestions, not submitted orders.
- Recommendation and Trade Plan evidence is immutable and versioned.
- Actual fills are recorded separately from proposed entry values.
- Guardian output is advisory and includes data-freshness state.
- Every recommendation, registration, and Guardian evaluation is auditable and
  replayable through the governed event contracts.
- Broker credentials remain backend-only and are scoped to required read-only
  capabilities wherever the provider supports such scoping.

## Consequences

- PredictiveEdge avoids automated-execution risk and exchange-simulation scope.
- Trade planning, deterministic risk, monitoring, and explainability become the
  core product workflow.
- Paper execution code is isolated from live broker integrations.
- Manual trade registration is required before the initial Trade Guardian pilot.
- Read-only reconciliation may reduce manual entry later without changing the
  human-executed-order boundary.
