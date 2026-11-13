import { HistoricalSetupAnalysis, OllamaCandidate, RankedPreOpenCandidate } from './preopen';
import { MarketScreenAnalysisResponse, MarketScreenType } from './marketScreens';

type Props = {
  candidate: OllamaCandidate;
  facts?: HistoricalSetupAnalysis;
  ranking?: RankedPreOpenCandidate;
  capital: number;
  riskPercent: number;
};

const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const number = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });

function price(value?: number) {
  return value == null ? 'Not available' : money.format(Number(value));
}

function confirmationRule(rule?: string) {
  const rules: Record<string, string> = {
    BUY_ONLY_ABOVE_FIRST_5_MINUTE_HIGH_AND_SESSION_VWAP:
      'Buy only after a finalized 5-minute close is above both the first 5-minute high and session VWAP.',
    SELL_ONLY_BELOW_FIRST_5_MINUTE_LOW_AND_SESSION_VWAP:
      'Sell only after a finalized 5-minute close is below both the first 5-minute low and session VWAP.',
    SELL_ONLY_AFTER_FIRST_5_MINUTE_CLOSE_BELOW_PREVIOUS_CLOSE_AND_SESSION_VWAP:
      'Sell only after a finalized 5-minute close is below both the previous close and session VWAP.',
    BUY_ONLY_AFTER_FIRST_5_MINUTE_CLOSE_ABOVE_PREVIOUS_CLOSE_AND_SESSION_VWAP:
      'Buy only after a finalized 5-minute close is above both the previous close and session VWAP.',
    WAIT_FOR_FIRST_FINAL_5_MINUTE_BAR: 'Wait for the first finalized 5-minute bar before making a decision.',
    NO_ENTRY: 'No entry is permitted for this setup.',
  };
  return rule ? rules[rule] ?? rule.replaceAll('_', ' ').toLowerCase() : 'No executable trigger is available.';
}

export function PreOpenDecisionCard({ candidate, facts, ranking, capital, riskPercent }: Props) {
  const assessment = facts?.assessment;
  const isLong = assessment?.action === 'BUY_CANDIDATE' && candidate.stance === 'LONG_WATCH';
  const isShort = assessment?.action === 'SELL_CANDIDATE' && candidate.stance === 'SHORT_WATCH';
  const directional = isLong || isShort;
  const side = isLong ? 'BUY' : isShort ? 'SELL' : 'NO TRADE';
  const recommendation = directional ? `WAIT → ${side} ONLY IF TRIGGER CONFIRMS` : 'NO TRADE / KEEP ON WATCHLIST';
  const entry = assessment?.referenceEntry == null ? undefined : Number(assessment.referenceEntry);
  const stop = assessment?.indicativeStop == null ? undefined : Number(assessment.indicativeStop);
  const targetOne = assessment?.indicativeTargetOne == null ? undefined : Number(assessment.indicativeTargetOne);
  const targetTwo = assessment?.indicativeTargetTwo == null ? undefined : Number(assessment.indicativeTargetTwo);
  const riskPerShare = entry != null && stop != null ? Math.abs(entry - stop) : undefined;
  const rewardOne = entry != null && targetOne != null ? Math.abs(targetOne - entry) : undefined;
  const rewardTwo = entry != null && targetTwo != null ? Math.abs(targetTwo - entry) : undefined;
  const riskAmount = capital > 0 && riskPercent > 0 ? capital * riskPercent / 100 : undefined;
  const quantity = riskAmount != null && riskPerShare != null && riskPerShare > 0
    ? Math.floor(riskAmount / riskPerShare) : undefined;
  const tone = isLong ? 'buy' : isShort ? 'sell' : 'wait';

  return <article className={`decision-card ${tone}`}>
    <div className="decision-header">
      <div><span className="decision-kicker">{assessment?.setupType?.replaceAll('_', ' ') ?? 'SCREEN RESULT'}</span><h3>{candidate.symbol}</h3></div>
      <span className={`decision-side ${tone}`}>{side}</span>
    </div>

    <div className={`recommendation-strip ${tone}`}>
      <div><small>PROPOSED ACTION</small><strong>{recommendation}</strong></div>
      <span>{directional ? 'Trigger pending' : 'Not actionable'}</span>
    </div>

    <div className="decision-meta">
      {ranking && <span><small>{ranking.direction === 'GAINER' ? 'GAINER' : 'LOSER'} RANK</small><b>#{ranking.rank}</b></span>}
      {ranking && <span><small>SCREEN SCORE</small><b>{Number(ranking.rankingScore).toFixed(1)} · {ranking.scoreBand}</b></span>}
      <span><small>GAP</small><b>{assessment ? `${Number(assessment.gapPercent).toFixed(2)}%` : '—'}</b></span>
      <span><small>HORIZON</small><b>Intraday only</b></span>
    </div>

    {directional && <>
      <div className="implementation-gate">
        <span>ENTRY GATE · NOT YET CLEARED</span>
        <b>{confirmationRule(assessment?.confirmationRule)}</b>
        <p>The reference entry below is an analytical anchor, not an executable order price. Recalculate levels if the confirmed market entry differs materially.</p>
      </div>

      <div className="trade-levels">
        <span><small>REFERENCE ENTRY</small><b>{price(entry)}</b></span>
        <span className="stop"><small>INDICATIVE STOP</small><b>{price(stop)}</b></span>
        <span><small>TARGET 1 · ~1R</small><b>{price(targetOne)}</b></span>
        <span><small>TARGET 2 · ~2R</small><b>{price(targetTwo)}</b></span>
      </div>

      <div className="risk-grid">
        <div><small>RISK / SHARE</small><b>{price(riskPerShare)}</b></div>
        <div><small>REWARD : RISK · T1</small><b>{riskPerShare && rewardOne ? `${(rewardOne / riskPerShare).toFixed(2)}R` : '—'}</b></div>
        <div><small>REWARD : RISK · T2</small><b>{riskPerShare && rewardTwo ? `${(rewardTwo / riskPerShare).toFixed(2)}R` : '—'}</b></div>
        <div><small>ILLUSTRATIVE RISK QTY</small><b>{quantity == null ? '—' : number.format(quantity)}</b></div>
      </div>
      <p className="sizing-note">Quantity uses ₹{number.format(riskAmount ?? 0)} risk budget ({riskPercent}% of ₹{number.format(capital)}) divided by indicative risk/share. Apply broker margin, liquidity, slippage and portfolio limits before any order.</p>
    </>}

    <div className="evidence-grid">
      <div><small>TECHNICAL EVIDENCE</small><p>EMA20 {price(assessment?.ema20)} · EMA50 {price(assessment?.ema50)} · RSI14 {assessment?.rsi14 == null ? '—' : Number(assessment.rsi14).toFixed(1)} · ATR14 {price(assessment?.atr14)}</p></div>
      <div><small>EXECUTION QUALITY</small><p>Spread {assessment ? Number(assessment.spreadBps).toFixed(1) : '—'} bps · Auction volume {assessment?.auctionVolumeRatio == null ? '—' : `${Number(assessment.auctionVolumeRatio).toFixed(2)}×`} · {facts?.historicalBars ?? 0} historical bars</p></div>
      <div><small>MODEL THESIS</small><p>{candidate.thesis}</p></div>
      <div><small>PRIMARY RISK / KILL CONDITION</small><p>{candidate.risk}</p></div>
    </div>

    <div className="action-rules">
      <b>Action discipline</b>
      <ul>
        <li>{directional ? `Do not ${side.toLowerCase()} before the opening-range and VWAP trigger is confirmed.` : 'Do not initiate a position from this pre-open result.'}</li>
        {directional && <li>Cancel the setup if confirmation never occurs, spread/liquidity deteriorates, or the stop distance exceeds the chosen risk budget.</li>}
        {directional && <li>Respect the indicative stop after entry; Target 1 and Target 2 represent approximately 1R and 2R from the reference level.</li>}
        <li>Close or independently re-underwrite before the session ends; this screen does not authorize overnight exposure.</li>
      </ul>
    </div>
    <p className="score-warning">Screen score is a relative ranking—not a calibrated win probability. This is decision support, not an order or guarantee.</p>
  </article>;
}

type ScreenCandidate = MarketScreenAnalysisResponse['ollama']['candidates'][number];
type ScreenFacts = MarketScreenAnalysisResponse['deterministic']['analyses'][number];
type ScreenRanking = MarketScreenAnalysisResponse['deterministic']['ranking']['candidates'][number];

export function MarketScreenDecisionCard({ candidate, facts, ranking, screenType, capital, riskPercent }: {
  candidate: ScreenCandidate;
  facts?: ScreenFacts;
  ranking?: ScreenRanking;
  screenType: MarketScreenType;
  capital: number;
  riskPercent: number;
}) {
  const assessment = facts?.assessment;
  const isLong = assessment?.action === 'BUY_CANDIDATE' && candidate.stance === 'LONG_WATCH';
  const isShort = assessment?.action === 'SELL_CANDIDATE' && candidate.stance === 'SHORT_WATCH';
  const directional = isLong || isShort;
  const side = isLong ? 'BUY' : isShort ? 'SELL' : 'NO TRADE';
  const tone = isLong ? 'buy' : isShort ? 'sell' : 'wait';
  const recommendation = directional ? `WAIT → ${side} ONLY IF TRIGGER CONFIRMS` : 'NO TRADE / KEEP ON WATCHLIST';
  const entry = assessment?.referenceEntry == null ? undefined : Number(assessment.referenceEntry);
  const stop = assessment?.indicativeStop == null ? undefined : Number(assessment.indicativeStop);
  const targetOne = assessment?.indicativeTargetOne == null ? undefined : Number(assessment.indicativeTargetOne);
  const targetTwo = assessment?.indicativeTargetTwo == null ? undefined : Number(assessment.indicativeTargetTwo);
  const riskPerShare = entry != null && stop != null ? Math.abs(entry - stop) : undefined;
  const rewardOne = entry != null && targetOne != null ? Math.abs(targetOne - entry) : undefined;
  const rewardTwo = entry != null && targetTwo != null ? Math.abs(targetTwo - entry) : undefined;
  const riskAmount = capital > 0 && riskPercent > 0 ? capital * riskPercent / 100 : undefined;
  const quantity = riskAmount != null && riskPerShare != null && riskPerShare > 0
    ? Math.floor(riskAmount / riskPerShare) : undefined;
  const category = screenType.replaceAll('_', ' ');

  return <article className={`decision-card ${tone}`}>
    <div className="decision-header">
      <div><span className="decision-kicker">{category}</span><h3>{candidate.symbol}</h3></div>
      <span className={`decision-side ${tone}`}>{side}</span>
    </div>

    <div className={`recommendation-strip ${tone}`}>
      <div><small>PROPOSED ACTION</small><strong>{recommendation}</strong></div>
      <span>{directional ? 'Trigger pending' : 'Not actionable'}</span>
    </div>

    <div className="decision-meta">
      <span><small>SCREEN RANK</small><b>{ranking ? `#${ranking.rank}` : '—'}</b></span>
      <span><small>SCREEN SCORE</small><b>{ranking ? `${Number(ranking.score).toFixed(1)} · ${ranking.scoreBand}` : '—'}</b></span>
      <span><small>MOVE</small><b>{assessment ? `${Number(assessment.percentChange).toFixed(2)}%` : '—'}</b></span>
      <span><small>HORIZON</small><b>Intraday only</b></span>
    </div>

    {directional && <>
      <div className="implementation-gate">
        <span>ENTRY GATE · NOT YET CLEARED</span><b>{confirmationRule(assessment?.confirmationRule)}</b>
        <p>The reference entry is an analytical anchor. Use these levels only after the next-session opening-range and VWAP confirmation, and recalculate if the confirmed entry differs materially.</p>
      </div>

      <div className="trade-levels">
        <span><small>REFERENCE ENTRY</small><b>{price(entry)}</b></span>
        <span className="stop"><small>INDICATIVE STOP</small><b>{price(stop)}</b></span>
        <span><small>TARGET 1 · ~1R</small><b>{price(targetOne)}</b></span>
        <span><small>TARGET 2 · ~2R</small><b>{price(targetTwo)}</b></span>
      </div>

      <div className="risk-grid">
        <div><small>RISK / SHARE</small><b>{price(riskPerShare)}</b></div>
        <div><small>REWARD : RISK · T1</small><b>{riskPerShare && rewardOne ? `${(rewardOne / riskPerShare).toFixed(2)}R` : '—'}</b></div>
        <div><small>REWARD : RISK · T2</small><b>{riskPerShare && rewardTwo ? `${(rewardTwo / riskPerShare).toFixed(2)}R` : '—'}</b></div>
        <div><small>ILLUSTRATIVE RISK QTY</small><b>{quantity == null ? '—' : number.format(quantity)}</b></div>
      </div>
      <p className="sizing-note">Quantity uses ₹{number.format(riskAmount ?? 0)} risk budget ({riskPercent}% of ₹{number.format(capital)}) divided by indicative risk/share. Apply margin, liquidity, slippage and portfolio limits before any order.</p>
    </>}

    <div className="evidence-grid">
      <div><small>TECHNICAL EVIDENCE</small><p>EMA20 {price(assessment?.ema20)} · EMA50 {price(assessment?.ema50)} · RSI14 {assessment?.rsi14 == null ? '—' : Number(assessment.rsi14).toFixed(1)} · ATR14 {price(assessment?.atr14)}</p></div>
      <div><small>MARKET / LIQUIDITY EVIDENCE</small><p>Move {assessment ? `${Number(assessment.percentChange).toFixed(2)}%` : '—'} · Volume {number.format(assessment?.screenVolume ?? 0)} · Turnover {money.format(Number(assessment?.screenTurnover ?? 0))} · {facts?.historicalBars ?? 0} historical bars</p></div>
      <div><small>OLLAMA THESIS</small><p>{candidate.thesis}</p></div>
      <div><small>PRIMARY RISK / KILL CONDITION</small><p>{candidate.risk}</p></div>
    </div>

    <div className="action-rules"><b>Action discipline</b><ul>
      <li>{directional ? `Do not ${side.toLowerCase()} before the opening-range and VWAP trigger is confirmed.` : 'Do not initiate a position from this screen result.'}</li>
      {directional && <li>Cancel the setup if confirmation never occurs, liquidity deteriorates, or the stop exceeds the selected risk budget.</li>}
      {directional && <li>Respect the indicative stop after entry; the targets represent approximately 1R and 2R from the reference level.</li>}
      <li>Close or independently re-underwrite before the session ends; this screen does not authorize overnight exposure.</li>
    </ul></div>
    <p className="score-warning">Screen score is a relative ranking—not a calibrated win probability. This is decision support, not an order or guarantee.</p>
  </article>;
}
