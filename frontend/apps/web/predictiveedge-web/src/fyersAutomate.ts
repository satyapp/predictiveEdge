export type TradeAction = 'BUY' | 'SELL' | 'WAIT' | 'NO_TRADE';
export type RecommendationStage = 'PREDICTIVEEDGE_STAGE_ONE' | 'FIA_REVIEW' | 'FINAL_AI_FIA' | 'GUARDIAN_REVISION';
export type ReviewStatus = 'STAGE_ONE_CREATED' | 'STAGE_ONE_SENT' | 'AWAITING_FIA_REVIEW' | 'FIA_REVIEW_RECORDED'
  | 'FINAL_READY' | 'FINAL_SENT' | 'CLOSED';

export type AutomateWebhook = {
  webhookId: string; label: string; maskedUrl: string; enabled: boolean; createdAt: string; lastDeliveredAt?: string;
};

export type RecommendationDraft = {
  action: TradeAction;
  entryLow?: number | null; entryHigh?: number | null; stopLoss?: number | null;
  targetOne?: number | null; targetTwo?: number | null; quantity?: number | null; confidence?: number | null;
  rationale: string;
};

export type RecommendationVersion = RecommendationDraft & {
  versionId: string; stage: RecommendationStage; source: string; createdAt: string;
};

export type WebhookDelivery = {
  deliveryId: string; reviewId?: string; webhookId?: string; stage?: RecommendationStage; sentAt: string;
  httpStatus?: number; succeeded: boolean; error?: string;
};

export type TradeOutcome = {
  direction: 'BUY' | 'SELL'; quantity: number; entryPrice: number; exitPrice: number; enteredAt: string; exitedAt: string;
  charges: number; realizedPnl: number; returnPercent: number; followedStage: RecommendationStage; notes: string; recordedAt: string;
};

export type RecommendationReview = {
  reviewId: string; exchange: string; symbol: string; fyersSymbol: string; sourceRef: string; status: ReviewStatus;
  createdAt: string; updatedAt: string; versions: RecommendationVersion[]; deliveries: WebhookDelivery[];
  outcome?: TradeOutcome | null; liveOrderPlaced: false;
};

export type LearningSummary = {
  closedTrades: number; winningTrades: number; winRatePercent: number; totalPnl: number;
  byFollowedStage: Partial<Record<RecommendationStage, { trades: number; wins: number; pnl: number }>>;
  fiaAgreedWithStageOne: number; fiaDisagreedWithStageOne: number; stageOneDirectionCorrect: number; fiaDirectionCorrect: number;
};

export type OutcomeInput = {
  direction: 'BUY' | 'SELL'; quantity: number; entryPrice: number; exitPrice: number; enteredAt: string; exitedAt: string;
  charges?: number; followedStage?: RecommendationStage; notes?: string;
};

export const stageLabels: Record<RecommendationStage, string> = {
  PREDICTIVEEDGE_STAGE_ONE: 'PredictiveEdge · stage 1',
  FIA_REVIEW: 'FIA review',
  FINAL_AI_FIA: 'Final · AI + FIA',
  GUARDIAN_REVISION: 'Trade Guardian revision',
};

export const statusLabels: Record<ReviewStatus, string> = {
  STAGE_ONE_CREATED: 'Not sent yet',
  STAGE_ONE_SENT: 'Sent to FYERS',
  AWAITING_FIA_REVIEW: 'Waiting for FIA review',
  FIA_REVIEW_RECORDED: 'FIA review recorded',
  FINAL_READY: 'Final ready to send',
  FINAL_SENT: 'Final sent · confirm in FYERS',
  CLOSED: 'Closed',
};

async function request<T>(path: string, accessToken: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/fyers-automate/v1${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(payload.message ?? `FYERS Automate request failed (HTTP ${response.status}).`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

const post = (body?: unknown): RequestInit => ({ method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });

export const loadWebhooks = (token: string) => request<AutomateWebhook[]>('/webhooks', token);
export const registerWebhook = (token: string, label: string, url: string) =>
  request<AutomateWebhook>('/webhooks', token, post({ label, url }));
export const setWebhookEnabled = (token: string, id: string, enabled: boolean) =>
  request<AutomateWebhook>(`/webhooks/${id}`, token, { method: 'PATCH', body: JSON.stringify({ enabled }) });
export const deleteWebhook = (token: string, id: string) => request<void>(`/webhooks/${id}`, token, { method: 'DELETE' });
export const testWebhook = (token: string, id: string) => request<WebhookDelivery>(`/webhooks/${id}/test`, token, post());

export const createReview = (token: string, body: {
  exchange?: string; symbol: string; sourceRef?: string; source?: string; webhookId?: string; recommendation: RecommendationDraft;
}) => request<RecommendationReview>('/reviews', token, post(body));
export const loadReviews = (token: string, symbol?: string) =>
  request<RecommendationReview[]>(`/reviews?limit=200${symbol ? `&symbol=${encodeURIComponent(symbol)}` : ''}`, token);
export const loadReview = (token: string, id: string) => request<RecommendationReview>(`/reviews/${id}`, token);
export const deliverReview = (token: string, id: string, stage: RecommendationStage, webhookId: string) =>
  request<WebhookDelivery>(`/reviews/${id}/deliveries`, token, post({ stage, webhookId }));
export const recordFiaReview = (token: string, id: string, draft: RecommendationDraft) =>
  request<RecommendationReview>(`/reviews/${id}/fia-review`, token, post(draft));
export const buildFinal = (token: string, id: string) => request<RecommendationReview>(`/reviews/${id}/final`, token, post());
export const recordGuardianRevision = (token: string, id: string, draft: RecommendationDraft) =>
  request<RecommendationReview>(`/reviews/${id}/guardian-revisions`, token, post(draft));
export const recordOutcome = (token: string, id: string, outcome: OutcomeInput) =>
  request<RecommendationReview>(`/reviews/${id}/outcome`, token, post(outcome));
export const loadLearning = (token: string, symbol?: string) =>
  request<LearningSummary>(`/learning${symbol ? `?symbol=${encodeURIComponent(symbol)}` : ''}`, token);

export function latest(review: RecommendationReview, stage: RecommendationStage) {
  return [...review.versions].reverse().find((version) => version.stage === stage);
}

/** Sends a PredictiveEdge stage-one recommendation to the first enabled webhook, or saves it unsent if none. */
export async function sendStageOne(token: string, symbol: string, sourceRef: string, draft: RecommendationDraft) {
  const webhooks = await loadWebhooks(token).catch(() => [] as AutomateWebhook[]);
  const webhook = webhooks.find((value) => value.enabled);
  const review = await createReview(token, { exchange: 'NSE', symbol, sourceRef, source: 'PredictiveEdge level 1 AI',
    webhookId: webhook?.webhookId, recommendation: draft });
  const delivery = review.deliveries.at(-1);
  return { review, webhook, delivered: delivery?.succeeded === true, error: delivery?.error };
}
