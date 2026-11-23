/**
 * Automation workspace domain model and data access.
 *
 * The page talks only to the `AutomationDataSource` interface below. Today it is backed by a
 * browser-only implementation (localStorage). When the backend is ready, add an HTTP implementation
 * of the same interface (see `marketScreens.ts` for the request style) and return it from
 * `createAutomationDataSource`.
 */

export type TradeAction = 'BUY' | 'SELL';

/** A stock handed to the automation workspace, usually from the Stock Search intraday list. */
export type AutomationCandidate = {
  symbol: string;
  category: string;
  rank: number;
  lastPrice?: number;
  percentChange?: number;
  reasoning?: string;
  score?: number;
};

/** A Level 2 AI trade recommendation. */
export type TradeRecommendation = {
  symbol: string;
  tradingDate: string;
  category: string;
  rank: number;
  confidence: number;
  action: TradeAction;
  entry: number;
  stopLoss: number;
  target1: number;
  target2: number;
  reasoning: string;
};

export type RiskSettings = { capital: number; riskPercent: number };

export type TradePlan = {
  valid: boolean;
  suggestedQuantity: number;
  quantity: number;
  plannedLoss: number;
  expectedProfit: number;
  capitalRequired: number;
  rewardToRisk: number | undefined;
};

export type BacktestSubmission = { accepted: boolean; message: string };

/** Review state of one stock inside an automation run. */
export type AutomationRunItem = {
  recommendation: TradeRecommendation;
  selected: boolean;
  quantityOverride: number | null;
  fiaRequested: boolean;
  fiaReasoning: string;
  fiaSaved: boolean;
  finalReasoning: string;
  finalSaved: boolean;
  backtestStatus?: string;
};

/** One automation run: the stocks sent from Stock Search on a given day and how far each got through review. */
export type AutomationRun = {
  id: string;
  tradingDate: string;
  createdAt: string;
  updatedAt: string;
  source: 'INTRADAY_LIST';
  settings: RiskSettings;
  items: AutomationRunItem[];
};

export interface AutomationDataSource {
  /** Human readable label shown on the page so nobody mistakes local data for live data. */
  readonly label: string;
  readonly live: boolean;
  /** Level 2 AI recommendations for the stocks sent from Stock Search. */
  loadRecommendations(request: { tradingDate: string; candidates: AutomationCandidate[] }): Promise<TradeRecommendation[]>;
  /** Past automation runs, newest first. */
  listRuns(): Promise<AutomationRun[]>;
  /** Create or update a run (called whenever its review state changes). */
  saveRun(run: AutomationRun): Promise<void>;
  requestFiaReview(request: { recommendation: TradeRecommendation; plan: TradePlan }): Promise<{ message: string }>;
  submitBacktest(request: { recommendation: TradeRecommendation; plan: TradePlan; settings: RiskSettings }): Promise<BacktestSubmission>;
}

export function newRunItem(recommendation: TradeRecommendation): AutomationRunItem {
  return { recommendation, selected: true, quantityOverride: null, fiaRequested: false, fiaReasoning: '', fiaSaved: false, finalReasoning: '', finalSaved: false };
}

export function isValidTradeSequence(trade: Pick<TradeRecommendation, 'action' | 'entry' | 'stopLoss' | 'target1' | 'target2'>): boolean {
  const { action, entry, stopLoss, target1, target2 } = trade;
  if (![entry, stopLoss, target1, target2].every(Number.isFinite) || entry <= 0 || entry === stopLoss) return false;
  return action === 'BUY'
    ? stopLoss < entry && entry < target1 && target1 <= target2
    : stopLoss > entry && entry > target1 && target1 >= target2;
}

/**
 * Per-trade position sizing: floor(min(risk budget / stop distance, capital / entry price)).
 * This is a per-trade calculation, not a portfolio allocator.
 */
export function suggestedQuantity(trade: Pick<TradeRecommendation, 'action' | 'entry' | 'stopLoss' | 'target1' | 'target2'>, settings: RiskSettings): number {
  if (!isValidTradeSequence(trade)) return 0;
  const capital = Math.max(0, settings.capital || 0);
  const riskPercent = Math.max(0, settings.riskPercent || 0);
  const stopDistance = Math.abs(trade.entry - trade.stopLoss);
  return Math.max(0, Math.min(Math.floor(capital * riskPercent / 100 / stopDistance), Math.floor(capital / trade.entry)));
}

export function tradePlan(trade: TradeRecommendation, settings: RiskSettings, quantityOverride: number | null = null): TradePlan {
  const valid = isValidTradeSequence(trade);
  const suggested = suggestedQuantity(trade, settings);
  const quantity = quantityOverride ?? suggested;
  const stopDistance = Math.abs(trade.entry - trade.stopLoss);
  const reward = Math.abs(trade.target1 - trade.entry);
  return {
    valid,
    suggestedQuantity: suggested,
    quantity,
    plannedLoss: quantity * stopDistance,
    expectedProfit: quantity * reward,
    capitalRequired: quantity * trade.entry,
    rewardToRisk: valid ? reward / stopDistance : undefined,
  };
}

export function riskBudget(settings: RiskSettings): number {
  return Math.max(0, settings.capital || 0) * Math.max(0, settings.riskPercent || 0) / 100;
}

export function validRiskSettings(settings: RiskSettings): boolean {
  return settings.capital > 0 && settings.riskPercent > 0 && settings.riskPercent <= 100;
}

/* ------------------------------------------------------------------------------------------ */
/* Local data source                                                                           */
/* ------------------------------------------------------------------------------------------ */

const tick = (value: number) => Math.round(value * 20) / 20; // NSE equity tick size is ₹0.05

/** Deterministic illustrative levels for a candidate so the same stock always shows the same demo plan. */
function demoRecommendation(candidate: AutomationCandidate, tradingDate: string): TradeRecommendation {
  const hash = [...candidate.symbol].reduce((total, character) => total + character.charCodeAt(0), 0);
  const action: TradeAction = ((candidate.percentChange ?? 0) < 0 ? 'SELL' : 'BUY');
  const entry = candidate.lastPrice && candidate.lastPrice > 0 ? tick(candidate.lastPrice) : 500 + hash % 1500;
  const direction = action === 'BUY' ? 1 : -1;
  const stopDistance = Math.max(0.05, tick(entry * (0.005 + (hash % 4) / 1000)));
  const reasoning = candidate.reasoning && candidate.reasoning !== 'Awaiting AI analysis.'
    ? candidate.reasoning
    : `${candidate.category} candidate from today's intraday list. Illustrative ${action === 'BUY' ? 'long' : 'short'} setup: confirm volume and broader market direction before entry.`;
  return {
    symbol: candidate.symbol,
    tradingDate,
    category: candidate.category || 'Intraday List',
    rank: candidate.rank,
    confidence: candidate.score && candidate.score > 0 ? Math.min(99, Math.round(candidate.score)) : 70 + hash % 20,
    action,
    entry,
    stopLoss: tick(entry - direction * stopDistance),
    target1: tick(entry + direction * stopDistance * 1.5),
    target2: tick(entry + direction * stopDistance * 2.5),
    reasoning,
  };
}

/**
 * Browser-only data source used until the backend exists. Recommendations are illustrative levels
 * derived from the stocks sent from Stock Search; runs are kept in this browser's localStorage per user.
 */
export function createLocalAutomationDataSource(userId: string): AutomationDataSource {
  const key = `predictiveedge.automation.runs.${userId}`;
  const read = (): AutomationRun[] => {
    try { const value = localStorage.getItem(key); return value ? JSON.parse(value) as AutomationRun[] : []; }
    catch { return []; }
  };
  return {
    label: 'Saved in this browser · no backend or broker connected',
    live: false,
    async loadRecommendations({ tradingDate, candidates }) {
      return candidates.map((candidate) => demoRecommendation(candidate, tradingDate));
    },
    async listRuns() {
      return read().sort((left, right) => right.createdAt.localeCompare(left.createdAt));
    },
    async saveRun(run) {
      const runs = [run, ...read().filter((item) => item.id !== run.id)].slice(0, 100);
      try { localStorage.setItem(key, JSON.stringify(runs)); } catch { /* storage full or blocked: keep the run in page state only */ }
    },
    async requestFiaReview({ recommendation }) {
      return { message: `FIA request for ${recommendation.symbol} staged locally. The FYERS FIA webhook is not connected.` };
    },
    async submitBacktest() {
      return { accepted: false, message: 'FYERS backtesting is not connected yet. No backtest was submitted.' };
    },
  };
}

/** Single swap point for the backend implementation. */
export function createAutomationDataSource(userId: string): AutomationDataSource {
  return createLocalAutomationDataSource(userId);
}
