import { FormEvent, useState } from 'react';
import { AuthSession } from './auth';
import { BrokerOverview } from './broker';
import { analyzeMarketScreenCsv, MarketScreenAnalysisResponse, MarketScreenType } from './marketScreens';

const supported: { value: MarketScreenType; label: string }[] = [
  { value: 'TOP_GAINERS', label: 'Top 20 Gainers' },
  { value: 'TOP_LOSERS', label: 'Top 20 Losers' },
];

const planned = ['New 52-week High / Low', 'Most Active Equities', 'Price Band Hitters', 'Volume Gainers'];
const compact = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });
const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });

export function MarketScreenAnalyzer({ session, broker }: { session: AuthSession; broker?: BrokerOverview }) {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const [screenType, setScreenType] = useState<MarketScreenType>('TOP_GAINERS');
  const [tradingDate, setTradingDate] = useState(today);
  const [file, setFile] = useState<File>();
  const [result, setResult] = useState<MarketScreenAnalysisResponse>();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    setBusy(true); setError(''); setResult(undefined);
    try {
      if (!broker?.zerodhaAccountId) throw new Error('Connect Zerodha before running historical analysis.');
      setResult(await analyzeMarketScreenCsv({ accessToken: session.accessToken, brokerAccountId: broker.zerodhaAccountId, screenType, tradingDate, file }));
    }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Market-screen analysis failed.'); }
    finally { setBusy(false); }
  }

  return <section className="market-screen-panel">
    <div className="market-screen-heading"><div><p className="eyebrow">MULTI-SCREEN IDEA FUNNEL</p><h2>NSE market-screen CSV intake</h2><p>Normalize each NSE screen first, then combine repeated symbols with Zerodha history and opening confirmation.</p></div><span>PHASE 1</span></div>
    <form className="market-screen-form" onSubmit={submit}>
      <label>Screen category<select value={screenType} onChange={(event) => { setScreenType(event.target.value as MarketScreenType); setResult(undefined); }}>{supported.map((value) => <option value={value.value} key={value.value}>{value.label}</option>)}</select></label>
      <label>Trading date<input type="date" value={tradingDate} onChange={(event) => setTradingDate(event.target.value)} required /></label>
      <label className="file-field">NSE screen CSV<input type="file" accept=".csv,text/csv" onChange={(event) => setFile(event.target.files?.[0])} required /><span>{file?.name ?? 'Choose CSV file'}</span></label>
      <button className="primary" disabled={busy || !file || !broker?.zerodhaConnected}>{busy ? 'Running history and Ollama…' : 'Analyze screen candidates'}</button>
    </form>
    <p className="planned-formats"><b>Next adapters:</b> {planned.join(' · ')}</p>
    {error && <p className="error" role="alert">{error}</p>}
    {result && <div className="market-screen-results">
      <div className="screen-result-summary"><div><small>SOURCE</small><b>{result.deterministic.screenType.replaceAll('_', ' ')}</b></div><div><small>USABLE ROWS</small><b>{result.deterministic.parsedRows}</b></div><div><small>MODEL</small><b>{result.ollama.model}</b></div><p>Relative idea priority only. The score is not a win probability and does not clear a BUY/SELL trigger.</p></div>
      <div className="screen-ranking-head"><span>Rank</span><span>Symbol</span><span>Watch</span><span>Move</span><span>LTP</span><span>Volume</span><span>Turnover</span><span>Score</span></div>
      {result.deterministic.ranking.candidates.map((candidate) => <div className="screen-ranking-row" key={candidate.symbol}>
        <span>#{candidate.rank}<small>source #{candidate.sourceRank}</small></span>
        <b>{candidate.symbol}</b>
        <span className={candidate.stance === 'LONG_WATCH' ? 'screen-long' : 'screen-short'}>{candidate.stance.replace('_', ' ')}</span>
        <span className={candidate.percentChange >= 0 ? 'positive' : 'negative'}>{Number(candidate.percentChange).toFixed(2)}%</span>
        <span>{money.format(candidate.lastPrice)}</span>
        <span>{compact.format(candidate.volume)}</span>
        <span>{money.format(candidate.turnover)}</span>
        <span><b>{Number(candidate.score).toFixed(1)}</b><small>{candidate.scoreBand}</small></span>
        <details><summary>Why this rank</summary><ul>{candidate.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul></details>
      </div>)}
      {result.deterministic.rejectedRows.length > 0 && <p className="row-note">{result.deterministic.rejectedRows.length} malformed or directionally inconsistent rows were excluded.</p>}
      <div className="final-gate"><b>Research priority, not a trade recommendation</b><span>Category rank must still be combined with other screens, Zerodha trend/volume history, liquidity, and the post-open trigger before a proposed trade becomes actionable.</span></div>
    </div>}
  </section>;
}
