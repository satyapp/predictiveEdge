import { useEffect, useMemo, useState } from 'react';
import { AuthSession } from './auth';
import { BrokerOverview } from './broker';
import { MarketScreenDecisionCard } from './PreOpenDecisionCard';
import {
  analyzeLiveMarketScreen, LiveMarketScreenAnalysisResponse, LiveMarketScreenCapability,
  loadLiveMarketScreenCapabilities, MarketScreenType,
} from './marketScreens';
import './live-stock-discovery.css';

type Category = 'TOP_MOVERS' | 'MOST_ACTIVE_EQUITIES' | 'PRICE_BAND_HITTERS' | 'VOLUME_GAINERS' | 'NEW_52_WEEK';

const categories: { value: Category; label: string; icon: string }[] = [
  { value: 'TOP_MOVERS', label: 'Top 20 Gainers / Losers', icon: '↗' },
  { value: 'MOST_ACTIVE_EQUITIES', label: 'Most Active Equities', icon: '▥' },
  { value: 'PRICE_BAND_HITTERS', label: 'Price Band Hitters', icon: '⬡' },
  { value: 'VOLUME_GAINERS', label: 'Volume Gainers', icon: '▤' },
  { value: 'NEW_52_WEEK', label: 'New 52-Week High / Low', icon: '⌁' },
];

function screenType(category: Category, direction: 'UP' | 'DOWN'): MarketScreenType {
  if (category === 'TOP_MOVERS') return direction === 'UP' ? 'TOP_GAINERS' : 'TOP_LOSERS';
  if (category === 'NEW_52_WEEK') return direction === 'UP' ? 'NEW_52_WEEK_HIGH' : 'NEW_52_WEEK_LOW';
  return category;
}

export function LiveStockDiscovery({ session, broker, onOpenManual }: {
  session: AuthSession; broker?: BrokerOverview; onOpenManual: () => void;
}) {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const [category, setCategory] = useState<Category>('TOP_MOVERS');
  const [direction, setDirection] = useState<'UP' | 'DOWN'>('UP');
  const [maximumCandidates, setMaximumCandidates] = useState(10);
  const [capabilities, setCapabilities] = useState<LiveMarketScreenCapability[]>([]);
  const [capabilityError, setCapabilityError] = useState('');
  const [result, setResult] = useState<LiveMarketScreenAnalysisResponse>();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const selectedType = screenType(category, direction);
  const capability = capabilities.find((value) => value.screenType === selectedType);
  const supported = capability?.available === true;
  const facts = useMemo(() => new Map(result?.deterministic.analyses.map((value) => [value.assessment.symbol, value])), [result]);
  const rankings = useMemo(() => new Map(result?.deterministic.ranking.candidates.map((value) => [value.symbol, value])), [result]);

  useEffect(() => {
    loadLiveMarketScreenCapabilities(session.accessToken).then((value) => {
      setCapabilities(value);
      if (value.length === 0) setCapabilityError('NSE MCP intake is disabled.');
    }).catch((failure) => setCapabilityError(
      failure instanceof Error ? failure.message : 'NSE MCP capabilities are unavailable.'));
  }, [session.accessToken]);

  async function run() {
    setBusy(true); setError(''); setResult(undefined);
    try {
      if (!broker?.zerodhaAccountId) throw new Error('Connect Zerodha before running live discovery analysis.');
      if (!supported) throw new Error(capability?.detail ?? 'This category is not exposed by the connected NSE MCP server.');
      setResult(await analyzeLiveMarketScreen({ accessToken: session.accessToken,
        brokerAccountId: broker.zerodhaAccountId, screenType: selectedType,
        tradingDate: today, maximumCandidates }));
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Live discovery could not be completed.'); }
    finally { setBusy(false); }
  }

  function select(value: Category) { setCategory(value); setResult(undefined); setError(''); }
  const asOf = result ? new Date(result.dataCopy.observedAt).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' }) : 'Awaiting live fetch';

  return <section className="live-discovery">
    <div className="live-title"><div><p className="eyebrow">NSE MARKET INTELLIGENCE</p><h2>Live Stock Discovery</h2><p>Fetch from NSE MCP, build the canonical PredictiveEdge data copy, then run the existing recommendation flow.</p></div><span>RESEARCH · NO AUTO-EXECUTION</span></div>
    <div className="source-health"><b>▦ NSE CM Market MCP</b><span className={`freshness ${result?.dataCopy.freshness.toLowerCase() ?? 'idle'}`}>{result?.dataCopy.freshness ?? (capabilityError ? 'UNAVAILABLE' : 'READY CHECK')}</span><span>As of: {asOf}</span><span>Source tool: {result?.dataCopy.source.split(':').at(-1) ?? capability?.sourceTool ?? '—'}</span><button type="button" onClick={() => loadLiveMarketScreenCapabilities(session.accessToken).then(setCapabilities)}>↻ Refresh status</button></div>
    {capabilityError && <p className="error" role="alert">{capabilityError} Enable PE_NSE_MCP_ENABLED to expose live discovery.</p>}

    <div className="live-categories">{categories.map((item) => {
      const candidateType = screenType(item.value, direction);
      const available = capabilities.find((value) => value.screenType === candidateType)?.available;
      return <button type="button" key={item.value} className={category === item.value ? 'selected' : ''} onClick={() => select(item.value)}><i>{item.icon}</i><b>{item.label}</b><small>{available ? 'NSE MCP available' : 'Capability not advertised'}</small></button>;
    })}</div>

    <div className="live-controls">
      <label>Universe<select disabled><option>NSE Mainboard Equity</option></select></label>
      {(category === 'TOP_MOVERS' || category === 'NEW_52_WEEK') && <label>Direction<div className="direction-toggle"><button type="button" className={direction === 'UP' ? 'active' : ''} onClick={() => setDirection('UP')}>{category === 'TOP_MOVERS' ? 'Gainers' : 'High'}</button><button type="button" className={direction === 'DOWN' ? 'active' : ''} onClick={() => setDirection('DOWN')}>{category === 'TOP_MOVERS' ? 'Losers' : 'Low'}</button></div></label>}
      <label>Maximum candidates<select value={maximumCandidates} onChange={(event) => setMaximumCandidates(Number(event.target.value))}><option>5</option><option>10</option><option>15</option><option>20</option></select></label>
      <div className="eligibility"><span>✓ EQ series only</span><span>✓ Rights & debt excluded</span><span>✓ Corporate-action evidence retained</span></div>
      <div className="live-actions"><button type="button" className="run-live" disabled={busy || !supported || !broker?.zerodhaConnected} onClick={run}>{busy ? 'Building data copy & analyzing…' : '⌕ Fetch & Rank Live Stocks'}</button><button type="button" className="manual-link" onClick={onOpenManual}>Use Manual CSV Instead</button></div>
    </div>
    {!supported && !capabilityError && <p className="capability-note">{capability?.detail ?? 'Checking the connected NSE MCP tool inventory…'} No alternate or partial dataset will be substituted.</p>}

    <div className="live-workflow"><span><i>1</i><b>NSE MCP intake</b></span><em>→</em><span><i>2</i><b>Canonical data copy</b></span><em>→</em><span><i>3</i><b>Existing analysis flow</b></span></div>
    {error && <p className="error" role="alert">{error}</p>}

    {result && <div className="live-results">
      <div className="ranked-table"><div className="result-heading"><div><h3>Ranked Candidates</h3><p>{result.deterministic.screenType.replaceAll('_', ' ')} · {result.deterministic.parsedRows} canonical rows</p></div><span>{result.ollama.marketTone}</span></div>
        <div className="rank-row header"><span>Rank</span><span>Symbol</span><span>LTP</span><span>Change</span><span>Volume</span><span>Score</span><span>Stance</span></div>
        {result.deterministic.ranking.candidates.map((candidate) => <div className="rank-row" key={candidate.symbol}><span>#{candidate.rank}</span><b>{candidate.symbol}</b><span>₹{Number(candidate.lastPrice).toLocaleString('en-IN')}</span><span className={candidate.percentChange >= 0 ? 'positive' : 'negative'}>{candidate.percentChange.toFixed(2)}%</span><span>{candidate.volume.toLocaleString('en-IN')}</span><strong>{candidate.score.toFixed(1)}</strong><em className={candidate.stance === 'LONG_WATCH' ? 'long' : 'short'}>{candidate.stance.replace('_', ' ')}</em></div>)}
      </div>
      <div className="live-decisions">{result.ollama.candidates.map((candidate) => <MarketScreenDecisionCard key={candidate.symbol} candidate={candidate} facts={facts.get(candidate.symbol)} ranking={rankings.get(candidate.symbol)} screenType={result.deterministic.screenType} capital={100000} riskPercent={0.5} />)}</div>
    </div>}
    <div className="freshness-rule">ⓘ Data becomes STALE after the governed freshness threshold. PredictiveEdge never silently substitutes EOD data for a live request.</div>
  </section>;
}
