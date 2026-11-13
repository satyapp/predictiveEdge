import { MarketScreenAnalysisResponse } from './marketScreens';
import { PreOpenAnalysisResponse } from './preopen';

export type AnalysisHistorySummary = {
  runId: string;
  category: string;
  resultKind: 'PRE_OPEN_FULL' | 'PRE_OPEN_DETERMINISTIC' | 'MARKET_SCREEN' | 'MARKET_SCREEN_FULL';
  fileName: string;
  sourceSha256: string;
  tradingDate: string;
  candidateCount: number;
  stageOneModel: string;
  createdAt: string;
};

export type AnalysisHistoryDetail = AnalysisHistorySummary & {
  result: PreOpenAnalysisResponse | MarketScreenAnalysisResponse;
};

async function failure(response: Response) {
  const payload = await response.json().catch(() => ({})) as { message?: string };
  return new Error(payload.message ?? `Analysis history request failed (HTTP ${response.status}).`);
}

export async function loadAnalysisHistory(accessToken: string) {
  const response = await fetch('/api/chart-intelligence/v1/analysis-history?limit=50', {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) throw await failure(response);
  return response.json() as Promise<AnalysisHistorySummary[]>;
}

export async function loadAnalysisHistoryDetail(accessToken: string, runId: string) {
  const response = await fetch(`/api/chart-intelligence/v1/analysis-history/${encodeURIComponent(runId)}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) throw await failure(response);
  return response.json() as Promise<AnalysisHistoryDetail>;
}
