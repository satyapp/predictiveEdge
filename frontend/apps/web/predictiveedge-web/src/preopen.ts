export type SetupAssessment = {
  venue: string;
  symbol: string;
  action: 'BUY_CANDIDATE' | 'SELL_CANDIDATE' | 'WAIT' | 'NO_TRADE' | 'INSUFFICIENT_EVIDENCE';
  setupType: string;
  confirmationRule: string;
  gapPercent: number;
  ema20?: number;
  ema50?: number;
  rsi14?: number;
  atr14?: number;
  spreadBps: number;
  auctionTurnover?: number;
  auctionVolumeRatio?: number;
  referenceEntry?: number;
  indicativeStop?: number;
  indicativeTargetOne?: number;
  indicativeTargetTwo?: number;
  analysisCutoff: string;
  reasons: string[];
  evidenceReferences: string[];
};

export type RankedPreOpenCandidate = {
  rank: number;
  venue: string;
  symbol: string;
  direction: 'GAINER' | 'LOSER';
  rankingScore: number;
  scoreBand: 'HIGH' | 'MEDIUM' | 'LOW';
  action: 'BUY_CANDIDATE' | 'SELL_CANDIDATE';
  setupType: string;
  gapPercent: number;
  reasons: string[];
};

export type HistoricalSetupAnalysis = {
  providerInstrumentId: string;
  historicalBars: number;
  assessment: SetupAssessment;
};

export type OllamaCandidate = {
  symbol: string;
  stance: 'LONG_WATCH' | 'SHORT_WATCH' | 'WAIT' | 'NO_TRADE' | 'INSUFFICIENT_EVIDENCE';
  thesis: string;
  confirmation: string;
  risk: string;
};

export type PreOpenAnalysisResponse = {
  authority: 'PRELIMINARY_NOT_FINAL';
  deterministic: {
    parsedSnapshots: number;
    shortlistedForHistory: number;
    analyses: HistoricalSetupAnalysis[];
    candidates: HistoricalSetupAnalysis[];
    ranking: {
      scoreVersion: string;
      calibratedProbability: boolean;
      evaluatedAssessments: number;
      probableGainers: RankedPreOpenCandidate[];
      probableLosers: RankedPreOpenCandidate[];
    };
    failures: { venue: string; symbol: string; reason: string }[];
    rejectedRows: { rowNumber: number; symbol: string; reason: string }[];
  };
  ollama: {
    model: string;
    generatedAt: string;
    invoked: boolean;
    marketTone: 'BULLISH' | 'BEARISH' | 'MIXED' | 'NO_SETUP';
    overview: string;
    candidates: OllamaCandidate[];
    cautions: string[];
  };
};

export async function analyzePreOpenCsv(input: {
  accessToken: string;
  brokerAccountId: string;
  tradingDate: string;
  file: File;
  historyDays?: number;
  maximumCandidates?: number;
}) {
  const form = new FormData();
  form.set('brokerAccountId', input.brokerAccountId);
  form.set('tradingDate', input.tradingDate);
  form.set('observedAt', `${input.tradingDate}T09:09:30+05:30`);
  form.set('analysisCutoff', `${input.tradingDate}T09:10:00+05:30`);
  form.set('historyDays', String(input.historyDays ?? 365));
  form.set('maximumCandidates', String(input.maximumCandidates ?? 10));
  form.set('file', input.file);
  const response = await fetch('/api/chart-intelligence/v1/pre-open-csv-analyses/ollama', {
    method: 'POST',
    headers: { Authorization: `Bearer ${input.accessToken}` },
    body: form,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { message?: string; fieldErrors?: { message?: string }[] };
    if (response.status === 504) {
      throw new Error('Pre-open analysis timed out while waiting for the AI model. Please try again.');
    }
    throw new Error(payload.fieldErrors?.find((value) => value.message)?.message
      ?? payload.message ?? `Pre-open analysis could not be completed (HTTP ${response.status}).`);
  }
  return response.json() as Promise<PreOpenAnalysisResponse>;
}
