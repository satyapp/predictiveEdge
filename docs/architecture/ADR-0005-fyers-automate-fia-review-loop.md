# ADR-0005: FYERS Automate and FIA Review Loop

- Status: Proposed
- Date: 2026-11-22
- Related: ADR-0004 (recommendation-only trading)

## Context

The trader uses FYERS Automate strategies built with FIA (the FYERS Intelligent Assistant) and wants
PredictiveEdge recommendations reviewed and corrected by FIA before acting on them.

What FYERS exposes externally (checked November 2026):

- Automate has an **inbound webhook trigger** per strategy. A POST starts the workflow. FYERS does not
  document a payload schema or authentication beyond the identifier in the URL. Community reports
  suggest it accepts roughly one call a minute.
- There is **no API or MCP tool** to create, read, start or stop Automate strategies or to talk to FIA.
  FIA runs on private endpoints of the FYERS web app, behind a Cloudflare check on login. The FYERS
  API terms allow access only "by the means described in the documentation" and forbid reverse
  engineering, so PredictiveEdge must not script FIA or call its internal endpoints.
- Automate has **no outbound webhook**. FIA's answer reaches the trader only inside FYERS (or as an
  email or app notification).

## Decision

PredictiveEdge implements a review-only loop in the new `fyers-automate-review` module:

1. **Stage 1.** PredictiveEdge stores its recommendation and POSTs it to a trader-registered Automate
   webhook. Webhook URLs must be HTTPS on `fyers.in` hosts and are stored encrypted with the broker
   credential cipher.
2. **FIA review.** The trader opens the strategy in FYERS Automate, reviews it with FIA, and enters
   FIA's levels and reasoning in PredictiveEdge. This manual step is the only supported way back,
   because FYERS offers no outbound channel.
3. **Final (AI + FIA).** The configured AI provider (OpenAI or Ollama, as in `predictiveedge.ai`)
   reconciles stage 1 with the FIA review and with the trader's realized track record. The output is
   validated, and a deterministic, conservative rule set is used when no provider is configured, the
   provider fails, or the model returns a direction neither side proposed.
4. **Back to FYERS.** The final recommendation is POSTed to the same webhook. The trader decides and
   confirms any live order in FYERS.
5. **Trade Guardian (placeholder).** Advisory `GUARDIAN_REVISION` versions can be recorded and sent to
   Automate. A `GuardianAdvisor` port is in place for future automatic monitoring.
6. **Outcome and learning.** After exit, the trader records the fills. Realized P/L is derived, and
   closed trades feed the reconciliation prompt and the Trade history page's track record (win rate,
   and how often PredictiveEdge and FIA were directionally right).

Every version is immutable and kept per stock: stage 1, FIA review, final, and Guardian revisions.
Every webhook delivery is audited with its payload and HTTP status.

## Guardrails (ADR-0004 still holds)

- Every payload carries `review_only: true` and `live_order: false`. It omits the `lots` key that
  community Automate examples map directly into order blocks.
- PredictiveEdge calls no broker write API. The trader is told to keep **Place order** blocks out of
  the Automate strategy wired to this webhook.
- Trade Guardian revisions are advisory and never modify an order.

> Open question for the owner: the request mentions Trade Guardian "modifying the order if needed".
> That would supersede ADR-0004 and FYERS API terms section 6 (no fully automated trades without
> exchange approval). It is deliberately **not** implemented. Guardian proposes; the trader acts.

## Consequences

- The loop works today with only documented FYERS features.
- The FIA step is manual (copy and paste) until FYERS publishes an FIA or Automate API. When it does,
  only the FIA intake step needs to change.
- A misconfigured Automate strategy (one with an order block) could still trade on a webhook call. That
  risk sits in FYERS, outside PredictiveEdge's control, and is called out in the UI.
