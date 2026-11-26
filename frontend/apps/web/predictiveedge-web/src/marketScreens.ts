import type { OllamaAnalysis, StageOneGoldenCopy } from './preopen';

export type MarketScreenType =
  | 'TOP_GAINERS'
  | 'TOP_LOSERS'
  | 'NEW_52_WEEK_HIGH'
  | 'NEW_52_WEEK_LOW'
  | 'MOST_ACTIVE_EQUITIES'
  | 'PRICE_BAND_HITTERS'
  | 'VOLUME_GAINERS'
  | 'INTRADAY_OPPORTUNITY';

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
  ollama: OllamaAnalysis;
  goldenCopy: StageOneGoldenCopy;
};

export type LiveMarketScreenCapability = {
  screenType: MarketScreenType;
  available: boolean;
  sourceTool: string;
  detail: string;
};

export type LiveMarketScreenAnalysisResponse = MarketScreenAnalysisResponse & {
  dataCopy: LiveMarketScreenDataCopy;
  searchId: string;
};

export type IntradayListSaveResponse = {
  listId: string;
  searchId: string;
  symbols: string[];
};

export type LiveMarketScreenDataCopy = {
    screenType: MarketScreenType;
    source: string;
    observedAt: string;
    fetchedAt: string;
    freshness: 'LIVE' | 'DELAYED' | 'STALE' | 'UNKNOWN';
    observations: { symbol: string; sourceRank: number; open: number; high: number; low: number;
      previousClose: number; lastPrice: number; percentChange: number; volume: number; turnover: number; observedAt: string }[];
    rejectedRows: { rowNumber: number; symbol: string; reason: string }[];
};

export type LiveMarketScreenSearchResponse = {
  authority: 'BROKER_SCREEN_ONLY';
  dataCopy: LiveMarketScreenDataCopy;
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

export async function loadLiveMarketScreenCapabilities(accessToken: string) {
  const response = await fetch('/api/chart-intelligence/v1/live-stock-discovery/capabilities', {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    if (response.status === 401) throw new Error('Your PredictiveEdge session has expired. Sign out and sign in again.');
    if (response.status === 404) return [] as LiveMarketScreenCapability[];
    throw new Error(`Live NSE MCP capabilities are unavailable (HTTP ${response.status}).`);
  }
  return response.json() as Promise<LiveMarketScreenCapability[]>;
}

export async function saveIntradayList(input: {
  accessToken: string;
  searchId: string;
  symbols: string[];
}) {
  const response = await fetch('/api/chart-intelligence/v1/intraday-lists', {
    method: 'POST',
    headers: { Authorization: `Bearer ${input.accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ searchId: input.searchId, symbols: input.symbols }),
  });
  if (!response.ok) {
    if (response.status === 401) throw new Error('Your PredictiveEdge session has expired. Sign out and sign in again.');
    const payload = await response.json().catch(() => ({})) as { message?: string; fieldErrors?: { message?: string }[] };
    throw new Error(payload.fieldErrors?.find((value) => value.message)?.message
      ?? payload.message ?? `The intraday list could not be saved (HTTP ${response.status}).`);
  }
  return response.json() as Promise<IntradayListSaveResponse>;
}

export async function analyzeLiveMarketScreen(input: {
  accessToken: string;
  brokerAccountId: string;
  screenType: MarketScreenType;
  tradingDate: string;
  maximumCandidates?: number;
  setup?: string;
  universe?: string;
  timeframe?: string;
  selectedSymbol?: string;
}) {
  const response = await fetch('/api/chart-intelligence/v1/live-stock-discovery/ollama', {
    method: 'POST',
    headers: { Authorization: `Bearer ${input.accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      brokerAccountId: input.brokerAccountId,
      screenType: input.screenType,
      tradingDate: input.tradingDate,
      historyDays: 365,
      maximumCandidates: input.maximumCandidates ?? 10,
      setup: input.setup ?? '',
      universe: input.universe ?? '',
      timeframe: input.timeframe ?? '',
      selectedSymbol: input.selectedSymbol ?? '',
    }),
  });
  if (!response.ok) {
    if (response.status === 401) throw new Error('Your PredictiveEdge session has expired. Sign out and sign in again.');
    const payload = await response.json().catch(() => ({})) as { message?: string; fieldErrors?: { message?: string }[] };
    throw new Error(payload.fieldErrors?.find((value) => value.message)?.message
      ?? payload.message ?? `Live market screen could not be analyzed (HTTP ${response.status}).`);
  }
  return response.json() as Promise<LiveMarketScreenAnalysisResponse>;
}

export async function searchLiveMarketScreen(input: {
  accessToken: string;
  brokerAccountId: string;
  screenType: MarketScreenType;
  tradingDate: string;
  maximumCandidates?: number;
  setup?: string;
  universe?: string;
  timeframe?: string;
  selectedSymbol?: string;
}) {
  const response = await fetch('/api/chart-intelligence/v1/live-stock-discovery/search', {
    method: 'POST',
    headers: { Authorization: `Bearer ${input.accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      brokerAccountId: input.brokerAccountId,
      screenType: input.screenType,
      tradingDate: input.tradingDate,
      historyDays: 365,
      maximumCandidates: input.maximumCandidates ?? 10,
      setup: input.setup ?? '',
      universe: input.universe ?? '',
      timeframe: input.timeframe ?? '',
      selectedSymbol: input.selectedSymbol ?? '',
    }),
  });
  if (!response.ok) {
    if (response.status === 401) throw new Error('Your PredictiveEdge session has expired. Sign out and sign in again.');
    const payload = await response.json().catch(() => ({})) as { message?: string; fieldErrors?: { message?: string }[] };
    throw new Error(payload.fieldErrors?.find((value) => value.message)?.message
      ?? payload.message ?? `FYERS stock search failed (HTTP ${response.status}).`);
  }
  return response.json() as Promise<LiveMarketScreenSearchResponse>;
}

export async function analyzeIntradayOpportunity(input: {
  accessToken: string;
  brokerAccountId: string;
  tradingDate: string;
  maximumCandidates?: number;
  setup?: string;
  universe?: string;
  timeframe?: string;
  selectedSymbol?: string;
}) {
  return analyzeLiveMarketScreen({ ...input, screenType: 'INTRADAY_OPPORTUNITY' });
}

export async function searchIntradayOpportunity(input: {
  accessToken: string;
  brokerAccountId: string;
  tradingDate: string;
  maximumCandidates?: number;
  setup?: string;
  universe?: string;
  timeframe?: string;
  selectedSymbol?: string;
}) {
  return searchLiveMarketScreen({ ...input, screenType: 'INTRADAY_OPPORTUNITY' });
}
