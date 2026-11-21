export type DomainView = { view: 'SUPPORTIVE' | 'NEUTRAL' | 'CONTRADICTORY' | 'NO_EVIDENCE' | string; note: string };

export type LevelTwoVerdict = {
  schemaVersion: string; symbol: string; levelOneRunId: string; levelOneStance: string; createdAt: string; model: string;
  action: 'BUY' | 'SELL' | 'WAIT' | 'NO_TRADE'; entryLow?: number | null; entryHigh?: number | null; stopLoss?: number | null;
  targetOne?: number | null; targetTwo?: number | null; confidence?: number | null; horizon?: string;
  domains: Record<string, DomainView>; keyRisks: string[]; rationale: string; validationNotes: string[];
};

export type QualifiedStock = {
  symbol: string; venue: string; runId: string; runKind: string; category: string; tradingDate?: string; analyzedAt: string;
  stance: string; qualified: boolean; levelOneModel: string; thesis: string; confirmation: string; risk: string;
  setupAction?: string | null; confirmationRule?: string | null; referenceEntry?: number | null; indicativeStop?: number | null;
  targetOne?: number | null; targetTwo?: number | null; rsi14?: number | null; atr14?: number | null; lastPrice?: number | null;
  percentChange?: number | null; rank?: number | null; score?: number | null; scoreBand?: string | null;
  evidence: Record<string, 'AVAILABLE' | 'MISSING'>; runCount: number; levelTwo?: LevelTwoVerdict | null;
};

export type PipelineOverview = { levelTwoAvailable: boolean; levelTwoModel: string; stocks: QualifiedStock[] };

async function request<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/stock-pipeline/v1${path}`, {
    ...init, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(payload.message ?? `Stock pipeline request failed (HTTP ${response.status}).`);
  }
  return response.json() as Promise<T>;
}

export const loadPipeline = (token: string, runs = 40) => request<PipelineOverview>(`/qualified-stocks?runs=${runs}`, token);
export const runLevelTwo = (token: string, symbol: string, runId?: string) =>
  request<LevelTwoVerdict>(`/qualified-stocks/${encodeURIComponent(symbol)}/level-two${runId ? `?runId=${runId}` : ''}`, token, { method: 'POST' });

export const runKindLabels: Record<string, string> = {
  PRE_OPEN_FULL: 'Pre-open CSV', PRE_OPEN_DETERMINISTIC: 'Pre-open CSV', MARKET_SCREEN_FULL: 'Market screen CSV',
  MARKET_SCREEN: 'Market screen CSV', CONSOLIDATED_LEVEL_ONE: 'Intraday screener',
};

export const domainLabels: Record<string, string> = {
  MARKET_DATA: 'Market data', TECHNICAL_ANALYSIS: 'Technical', CHART_PATTERNS: 'Chart patterns', FUNDAMENTAL_ANALYSIS: 'Fundamental',
  NEWS_AND_SENTIMENT: 'News & sentiment', CORPORATE_ACTIONS: 'Corporate actions', MACRO_AND_ECONOMIC: 'Macro',
  INSTITUTIONAL_FLOWS: 'Institutional flows', HISTORICAL_AND_PREDICTIVE: 'Historical',
  technical: 'Technical', fundamental: 'Fundamental', news_sentiment: 'News & sentiment', market_conditions: 'Market conditions',
  institutional_flows: 'Institutional flows', corporate_actions: 'Corporate actions',
};

/** The level the pipeline currently stands at for a stock. */
export function pipelineLevel(stock: QualifiedStock): 'LEVEL_TWO' | 'LEVEL_ONE_QUALIFIED' | 'LEVEL_ONE' {
  if (stock.levelTwo) return 'LEVEL_TWO';
  return stock.qualified ? 'LEVEL_ONE_QUALIFIED' : 'LEVEL_ONE';
}
