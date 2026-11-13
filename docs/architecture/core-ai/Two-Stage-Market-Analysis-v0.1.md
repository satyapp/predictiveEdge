# Two-stage market analysis — v0.1

Related end-to-end process:
[`Pre-Open-Market-Screen-Recommendation-Flow-v0.1.md`](./Pre-Open-Market-Screen-Recommendation-Flow-v0.1.md).

## Decision

PredictiveEdge runs deterministic screening and Ollama as the default Stage 1. OpenAI (or another
advanced model) is an optional, user-triggered Stage 2. Stage 2 refines the Stage-1 result; it does
not repeat NSE CSV parsing or fetch Zerodha history.

## Flow

1. Parse and validate the selected NSE category CSV.
2. Produce deterministic ranks and, where supported, enrich candidates with a point-in-time Zerodha snapshot.
3. Run Ollama and freeze the complete Stage-1 evidence plus model output.
4. Fingerprint and persist that immutable envelope.
5. On explicit user action, send the envelope through `AdvancedAnalysisPort`.
6. Persist the Stage-2 output against the same analysis id and input fingerprint.
7. Display Stage 1, Stage 2 and side-by-side comparison without reprocessing the source.

## Safety and provenance

- Stage 2 has no broker or historical-market-data dependency.
- Every result records model id, generation time and the input fingerprint.
- A changed CSV, connector snapshot or Stage-1 report creates a new analysis version.
- Confidence is model-reported evidence, not a calibrated win probability unless separately validated.
- BUY/SELL remains conditional on deterministic opening confirmation, liquidity, risk and execution gates.

## Phase 2 implementation backlog

- Persist analysis headers, frozen Stage-1 envelopes and per-stage results.
- Add user-scoped history queries with file/category/date filters.
- Implement the OpenAI adapter behind `AdvancedAnalysisPort` with structured output validation.
- Add idempotency by analysis id plus source fingerprint and explicit user-trigger audit events.
- Add Stage‑2 API and UI activation after secrets, cost limits, timeout policy and evaluation gates are ready.
