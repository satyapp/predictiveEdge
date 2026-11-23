import { describe, expect, it } from 'vitest';
import { demoAutomationDataSource, isValidTradeSequence, suggestedQuantity, tradePlan, TradeRecommendation } from './automation';

const buy: TradeRecommendation = { symbol: 'ICICIBANK', tradingDate: '2026-11-23', category: 'VWAP Breakout', rank: 1, confidence: 86, action: 'BUY', entry: 1616, stopLoss: 1607, target1: 1630, target2: 1640, reasoning: '' };
const sell: TradeRecommendation = { ...buy, symbol: 'RELIANCE', action: 'SELL', entry: 1460, stopLoss: 1472, target1: 1442, target2: 1430 };

describe('automation trade plan', () => {
  it('validates the stop and target order for each direction', () => {
    expect(isValidTradeSequence(buy)).toBe(true);
    expect(isValidTradeSequence(sell)).toBe(true);
    expect(isValidTradeSequence({ ...buy, stopLoss: 1620 })).toBe(false);
    expect(isValidTradeSequence({ ...sell, target1: 1470 })).toBe(false);
  });

  it('sizes by the smaller of risk budget and available capital', () => {
    // risk budget 1000 / stop 9 = 111, capital 100000 / 1616 = 61
    expect(suggestedQuantity(buy, { capital: 100000, riskPercent: 1 })).toBe(61);
    // risk budget 100 / stop 9 = 11
    expect(suggestedQuantity(buy, { capital: 100000, riskPercent: 0.1 })).toBe(11);
    expect(suggestedQuantity({ ...buy, stopLoss: 1700 }, { capital: 100000, riskPercent: 1 })).toBe(0);
  });

  it('computes profit, risk and reward to risk with a quantity override', () => {
    const plan = tradePlan(sell, { capital: 100000, riskPercent: 1 }, 10);
    expect(plan.quantity).toBe(10);
    expect(plan.plannedLoss).toBe(120);
    expect(plan.expectedProfit).toBe(180);
    expect(plan.rewardToRisk).toBeCloseTo(1.5);
  });

  it('builds valid demo recommendations from intraday list candidates', async () => {
    const result = await demoAutomationDataSource.loadRecommendations({ tradingDate: '2026-11-23', candidates: [
      { symbol: 'TCS', category: 'Top Gainers', rank: 1, lastPrice: 3412.35, percentChange: 1.2 },
      { symbol: 'SBIN', category: 'Top Losers', rank: 2, lastPrice: 801.1, percentChange: -0.8 },
    ] });
    expect(result.map((item) => item.action)).toEqual(['BUY', 'SELL']);
    expect(result.every(isValidTradeSequence)).toBe(true);
  });
});
