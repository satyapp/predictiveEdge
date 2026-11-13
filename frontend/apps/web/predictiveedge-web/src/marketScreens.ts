export type MarketScreenType =
  | 'TOP_GAINERS'
  | 'TOP_LOSERS'
  | 'NEW_52_WEEK_HIGH'
  | 'NEW_52_WEEK_LOW'
  | 'MOST_ACTIVE_EQUITIES'
  | 'PRICE_BAND_HITTERS'
  | 'VOLUME_GAINERS';

export type MarketScreenAnalysisResponse = {
  authority: 'PRELIMINARY_NOT_FINAL';
  deterministic: {
    authority: 'PRELIMINARY_NOT_FINAL';
    screenType: MarketScreenType;
    analyzedAt: string;
    parsedRows: number;
    rejectedRows: { rowNumber: number; symbol: string; reason: string }[];
    ranking: { scoreVersion: string; calibratedProbability: false; candidates: {
      rank: number;
      symbol: string;
      stance: 'LONG_WATCH' | 'SHORT_WATCH';
      score: number;
      scoreBand: 'HIGH' | 'MEDIUM' | 'LOW';
      percentChange: number;
      lastPrice: number;
      volume: number;
      turnover: number;
      sourceRank: number;
      reasons: string[];
    }[] };
    analyses: {
      providerInstrumentId: string;
      historicalBars: number;
      observation: { symbol: string; corporateAction: string; lastPrice: number; volume: number; turnover: number };
      assessment: {
        venue: string;
        symbol: string;
        action: 'BUY_CANDIDATE' | 'SELL_CANDIDATE' | 'WAIT' | 'NO_TRADE' | 'INSUFFICIENT_EVIDENCE';
        confirmationRule: string;
        percentChange: number;
        ema20?: number; ema50?: number; rsi14?: number; atr14?: number;
        screenVolume: number; screenTurnover: number;
        referenceEntry?: number; indicativeStop?: number;
        indicativeTargetOne?: number; indicativeTargetTwo?: number;
        analysisCutoff: string; reasons: string[]; evidenceReferences: string[];
      };
    }[];
    failures: { venue: string; symbol: string; reason: string }[];
  };
  ollama: {
    model: string; generatedAt: string; invoked: boolean;
    marketTone: 'BULLISH' | 'BEARISH' | 'MIXED' | 'NO_SETUP'; overview: string;
    candidates: { symbol: string; stance: 'LONG_WATCH' | 'SHORT_WATCH' | 'WAIT' | 'NO_TRADE' | 'INSUFFICIENT_EVIDENCE'; thesis: string; confirmation: string; risk: string }[];
    cautions: string[];
  };
};

export async function analyzeMarketScreenCsv(input: {
  accessToken: string;
  brokerAccountId: string;
  screenType: MarketScreenType;
  tradingDate: string;
  file: File;
  maximumCandidates?: number;
}) {
  const form = new FormData();
  form.set('brokerAccountId', input.brokerAccountId);
  form.set('screenType', input.screenType);
  form.set('tradingDate', input.tradingDate);
  form.set('observedAt', `${input.tradingDate}T15:30:00+05:30`);
  form.set('analysisCutoff', `${input.tradingDate}T15:30:00+05:30`);
  form.set('historyDays', '365');
  form.set('maximumCandidates', String(input.maximumCandidates ?? 10));
  form.set('file', input.file);
  const response = await fetch('/api/chart-intelligence/v1/market-screen-csv-analyses/ollama', {
    method: 'POST',
    headers: { Authorization: `Bearer ${input.accessToken}` },
    body: form,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { message?: string; fieldErrors?: { message?: string }[] };
    throw new Error(payload.fieldErrors?.find((value) => value.message)?.message
      ?? payload.message ?? `Market-screen CSV could not be analyzed (HTTP ${response.status}).`);
  }
  return response.json() as Promise<MarketScreenAnalysisResponse>;
}
