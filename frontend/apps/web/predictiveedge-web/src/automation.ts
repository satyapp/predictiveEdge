/**
 * Automation workspace domain model and data access.
 *
 * The page talks only to the `AutomationDataSource` interface below. Today it is backed by
 * `demoAutomationDataSource`, which keeps everything in the browser. When the backend is ready,
 * add an HTTP implementation of the same interface (see `marketScreens.ts` for the request style)
 * and return it from `createAutomationDataSource`.
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

export interface AutomationDataSource {
  /** Human readable label shown on the page so nobody mistakes demo data for live data. */
  readonly label: string;
  readonly live: boolean;
  loadRecommendations(request: { tradingDate: string; candidates?: AutomationCandidate[] }): Promise<TradeRecommendation[]>;
  requestFiaReview(request: { recommendation: TradeRecommendation; plan: TradePlan }): Promise<{ message: string }>;
  saveFiaReview(request: { tradingDate: string; symbol: string; reasoning: string }): Promise<void>;
  saveFinalReview(request: { tradingDate: string; symbol: string; reasoning: string }): Promise<void>;
  submitBacktest(request: { recommendation: TradeRecommendation; plan: TradePlan; settings: RiskSettings }): Promise<BacktestSubmission>;
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
/* Demo data source                                                                            */
/* ------------------------------------------------------------------------------------------ */

const demoSeed: Omit<TradeRecommendation, 'tradingDate'>[] = [
  { symbol: 'ICICIBANK', category: 'VWAP Breakout', rank: 1, confidence: 86, action: 'BUY', entry: 1616, stopLoss: 1607, target1: 1630, target2: 1640, reasoning: 'Price reclaimed VWAP with stronger relative volume. Confirm sustained breakout before entry.' },
  { symbol: 'INFY', category: 'Support / Resistance', rank: 2, confidence: 82, action: 'BUY', entry: 1510, stopLoss: 1498, target1: 1528, target2: 1542, reasoning: 'Support retest followed by bullish recovery. Confirm volume and resistance clearance.' },
  { symbol: 'RELIANCE', category: 'Previous Day Low', rank: 3, confidence: 79, action: 'SELL', entry: 1460, stopLoss: 1472, target1: 1442, target2: 1430, reasoning: 'Potential downside continuation below previous-day low; validate broader market weakness.' },
];

const tick = (value: number) => Math.round(value * 20) / 20; // NSE equity tick size is ₹0.05

/** Deterministic illustrative levels for a candidate so the same stock always shows the same demo plan. */
function demoRecommendation(candidate: AutomationCandidate, tradingDate: string): TradeRecommendation {
  const seeded = demoSeed.find((item) => item.symbol === candidate.symbol);
  const hash = [...candidate.symbol].reduce((total, character) => total + character.charCodeAt(0), 0);
  const action: TradeAction = seeded?.action ?? ((candidate.percentChange ?? 0) < 0 ? 'SELL' : 'BUY');
  const entry = candidate.lastPrice && candidate.lastPrice > 0 ? tick(candidate.lastPrice) : seeded?.entry ?? 500 + hash % 1500;
  const direction = action === 'BUY' ? 1 : -1;
  const stopDistance = Math.max(0.05, tick(entry * (0.005 + (hash % 4) / 1000)));
  const reasoning = candidate.reasoning && candidate.reasoning !== 'Awaiting AI analysis.'
    ? candidate.reasoning
    : seeded?.reasoning ?? `${candidate.category} candidate from today's intraday list. Illustrative ${action === 'BUY' ? 'long' : 'short'} setup: confirm volume and broader market direction before entry.`;
  return {
    symbol: candidate.symbol,
    tradingDate,
    category: candidate.category || seeded?.category || 'Intraday List',
    rank: candidate.rank,
    confidence: candidate.score && candidate.score > 0 ? Math.min(99, Math.round(candidate.score)) : seeded?.confidence ?? 70 + hash % 20,
    action,
    entry,
    stopLoss: tick(entry - direction * stopDistance),
    target1: tick(entry + direction * stopDistance * 1.5),
    target2: tick(entry + direction * stopDistance * 2.5),
    reasoning,
  };
}

export const demoAutomationDataSource: AutomationDataSource = {
  label: 'Demo data · no backend or broker connected',
  live: false,
  async loadRecommendations({ tradingDate, candidates }) {
    if (!candidates?.length) return demoSeed.map((item) => ({ ...item, tradingDate }));
    return candidates.map((candidate) => demoRecommendation(candidate, tradingDate));
  },
  async requestFiaReview({ recommendation }) {
    return { message: `FIA request for ${recommendation.symbol} staged locally. The FYERS FIA webhook is not connected.` };
  },
  async saveFiaReview() { /* kept in page state only */ },
  async saveFinalReview() { /* kept in page state only */ },
  async submitBacktest() {
    return { accepted: false, message: 'FYERS backtesting is not connected yet. No backtest was submitted.' };
  },
};

/** Single swap point for the backend implementation. */
export function createAutomationDataSource(): AutomationDataSource {
  return demoAutomationDataSource;
}
