import { useEffect, useMemo, useState } from 'react';
import { AuthSession } from './auth';
import { BrokerOverview } from './broker';
import { beginFyersConnection, disconnectFyersConnection, FyersConnectionStatus, loadFyersConnection } from './fyers';
import { MarketScreenDecisionCard } from './PreOpenDecisionCard';
import {
  analyzeIntradayOpportunity, LiveMarketScreenAnalysisResponse, LiveMarketScreenCapability, loadLiveMarketScreenCapabilities,
} from './marketScreens';
import './intraday-opportunity-engine.css';

type ScreenerCategory = {
  id: string; label: string; shortLabel: string; icon: string;
  direction: 'LONG' | 'SHORT' | 'BOTH'; description: string;
};

const categories: ScreenerCategory[] = [
  { id: 'MOST_TRADED', label: 'Most Traded', shortLabel: 'Turnover leaders', icon: '▥', direction: 'BOTH', description: 'Highest eligible traded value and executable liquidity.' },
  { id: 'INTRADAY_SURGES', label: 'Intraday Surges', shortLabel: 'Positive momentum', icon: '↗', direction: 'LONG', description: 'Strong positive movement with participation confirmation.' },
  { id: 'INTRADAY_FALLS', label: 'Intraday Falls', shortLabel: 'Negative momentum', icon: '↘', direction: 'SHORT', description: 'Strong negative movement with participation confirmation.' },
  { id: 'GAP_UP', label: 'Gap Up', shortLabel: 'Open above close', icon: '⇡', direction: 'LONG', description: 'Opening price materially above the previous close.' },
  { id: 'GAP_DOWN', label: 'Gap Down', shortLabel: 'Open below close', icon: '⇣', direction: 'SHORT', description: 'Opening price materially below the previous close.' },
  { id: 'WEEK_52_HIGH', label: '52W High', shortLabel: 'Periodic high', icon: '⌃', direction: 'LONG', description: 'Price testing or exceeding its governed 52-week high.' },
  { id: 'WEEK_52_LOW', label: '52W Low', shortLabel: 'Periodic low', icon: '⌄', direction: 'SHORT', description: 'Price testing or exceeding its governed 52-week low.' },
  { id: 'UNUSUAL_VOLUME', label: 'Unusual Volume', shortLabel: 'RVOL expansion', icon: '▤', direction: 'BOTH', description: 'Time-adjusted relative volume exceeds its quality threshold.' },
  { id: 'BREAKOUTS', label: 'Breakouts', shortLabel: 'Resistance crossed', icon: '⤴', direction: 'LONG', description: 'Confirmed upside range break with supporting participation.' },
  { id: 'BREAKDOWNS', label: 'Breakdowns', shortLabel: 'Support crossed', icon: '⤵', direction: 'SHORT', description: 'Confirmed downside range break with supporting participation.' },
];

type Candidate = LiveMarketScreenAnalysisResponse['deterministic']['ranking']['candidates'][number];

export function IntradayOpportunityEngine({ session, broker, connectionBusy, onConnectZerodha, onDisconnectZerodha }: {
  session: AuthSession;
  broker?: BrokerOverview;
  connectionBusy: boolean;
  onConnectZerodha: () => void;
  onDisconnectZerodha: () => void;
}) {
  const [selectedCategory, setSelectedCategory] = useState(categories[0].id);
  const [timeframe, setTimeframe] = useState('5');
  const [recommendationLimit, setRecommendationLimit] = useState('2');
  const [capability, setCapability] = useState<LiveMarketScreenCapability>();
  const [nseEnabled, setNseEnabled] = useState(true);
  const [trendlyneEnabled, setTrendlyneEnabled] = useState(true);
  const [fyers, setFyers] = useState<FyersConnectionStatus>();
  const [result, setResult] = useState<LiveMarketScreenAnalysisResponse>();
  const [consolidatedRuns, setConsolidatedRuns] = useState<Record<string, LiveMarketScreenAnalysisResponse>>({});
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>([]);
  const [expandedSymbol, setExpandedSymbol] = useState<string>();
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const category = categories.find((value) => value.id === selectedCategory) ?? categories[0];
  const categoryCandidates: Candidate[] = result?.deterministic.ranking.candidates.slice(0, Number(recommendationLimit)) ?? [];
  const consolidatedCandidates = selectedSymbols.map((symbol) => consolidatedRuns[symbol]?.deterministic.ranking.candidates
    .find((candidate) => candidate.symbol === symbol) ?? categoryCandidates.find((candidate) => candidate.symbol === symbol))
    .filter((candidate): candidate is Candidate => candidate !== undefined).slice(0, 5);
  const facts = useMemo(() => new Map(result?.deterministic.analyses.map((value) => [value.assessment.symbol, value])), [result]);
  const rankings = useMemo(() => new Map(result?.deterministic.ranking.candidates.map((value) => [value.symbol, value])), [result]);
  const aiCandidates = useMemo(() => new Map(result?.ollama.candidates.map((value) => [value.symbol, value])), [result]);

  useEffect(() => {
    void Promise.all([loadLiveMarketScreenCapabilities(session.accessToken)
      .then((values) => setCapability(values.find((value) => value.screenType === 'INTRADAY_OPPORTUNITY')))
      .catch(() => setCapability(undefined)), loadFyersConnection(session.accessToken).then(setFyers)
      .catch(() => setFyers(undefined))]);
    if (new URLSearchParams(window.location.search).get('provider') === 'fyers') {
      window.history.replaceState({}, '', window.location.pathname);
      setNotice('FYERS authorization completed. Refreshing connection status…');
      window.setTimeout(() => loadFyersConnection(session.accessToken).then((status) => {
        setFyers(status); setNotice(status.connected ? 'FYERS is connected and ready for intraday discovery.' : 'FYERS authorization did not complete.');
      }).catch((failure) => setNotice(failure instanceof Error ? failure.message : 'FYERS status is unavailable.')), 500);
    }
  }, [session.accessToken]);

  async function toggleFyersConnection() {
    setBusy(true); setNotice('');
    try {
      if (fyers?.connected) {
        await disconnectFyersConnection(session.accessToken); setFyers(await loadFyersConnection(session.accessToken));
      } else {
        window.location.href = (await beginFyersConnection(session.accessToken)).authorizationUrl;
      }
    } catch (failure) { setNotice(failure instanceof Error ? failure.message : 'FYERS connection could not be changed.'); setBusy(false); }
  }

  async function getStocks() {
    if (!fyers?.connected) return setNotice('Connect FYERS before requesting qualified stocks. NSE is not silently substituted.');
    if (!fyers.accountId) return setNotice('Reconnect FYERS because its account identity is unavailable.');
    setBusy(true); setNotice(''); setResult(undefined); setSelectedSymbols([]); setConsolidatedRuns({});
    try {
      setResult(await analyzeIntradayOpportunity({ accessToken: session.accessToken, brokerAccountId: fyers.accountId,
        tradingDate: today, maximumCandidates: Number(recommendationLimit), setup: selectedCategory,
        universe: 'NIFTY_500', timeframe }));
    } catch (failure) { setNotice(failure instanceof Error ? failure.message : 'Qualified-stock discovery failed.'); }
    finally { setBusy(false); }
  }

  async function toggleConsolidated(symbol: string) {
    if (selectedSymbols.includes(symbol)) {
      setSelectedSymbols((current) => current.filter((value) => value !== symbol)); return;
    }
    if (selectedSymbols.length >= 5 || !fyers?.accountId) return;
    setBusy(true); setNotice(`Running consolidated intelligence for ${symbol}…`);
    try {
      const consolidated = await analyzeIntradayOpportunity({ accessToken: session.accessToken,
        brokerAccountId: fyers.accountId, tradingDate: today, maximumCandidates: 20,
        setup: selectedCategory, universe: 'NIFTY_500', timeframe, selectedSymbol: symbol });
      setConsolidatedRuns((current) => ({ ...current, [symbol]: consolidated }));
      setSelectedSymbols((current) => [...current, symbol]); setNotice(`${symbol} consolidated level-one analysis is complete and persisted.`);
    } catch (failure) { setNotice(failure instanceof Error ? failure.message : `${symbol} consolidation failed.`); }
    finally { setBusy(false); }
  }

  return <section className="intraday-engine">
    <div className="intraday-title"><div><p className="eyebrow">NIFTY 500 · LIVE INTRADAY RESEARCH</p><h2>Intraday Screener</h2><p>Select a setup, scan the chosen universe, review first-level qualified stocks, then promote only the strongest evidence into consolidated analysis.</p></div><span>RESEARCH · NO AUTO-EXECUTION</span></div>

    <section className="connection-bar" aria-label="Data source connections">
      <div><p className="eyebrow">CONNECTIONS</p><h3>Research data sources</h3></div>
      <div className="connection-toggles">
        <button type="button" role="switch" aria-checked={broker?.zerodhaConnected === true} className={broker?.zerodhaConnected ? 'connected' : ''} disabled={connectionBusy || !broker?.zerodhaConfigured} onClick={broker?.zerodhaConnected ? onDisconnectZerodha : onConnectZerodha}><span><i>Z</i><b>Zerodha</b><small>{broker?.zerodhaConnected ? broker.zerodhaAccountId ?? 'Connected' : broker?.zerodhaConfigured ? 'Ready' : 'Setup required'}</small></span><em /></button>
        <button type="button" role="switch" aria-checked={fyers?.connected === true} className={fyers?.connected ? 'connected' : ''} disabled={busy || fyers?.configured === false} onClick={() => void toggleFyersConnection()}><span><i>F</i><b>FYERS</b><small>{fyers?.connected ? fyers.accountId ?? 'Connected' : fyers?.configured === false ? 'Setup required' : 'Primary discovery'}</small></span><em /></button>
        <button type="button" role="switch" aria-checked={nseEnabled && capability?.available === true} className={nseEnabled && capability?.available ? 'connected' : ''} disabled={!capability?.available} onClick={() => setNseEnabled((value) => !value)}><span><i>N</i><b>NSE</b><small>{capability?.available ? 'Fallback available' : 'Unavailable'}</small></span><em /></button>
        <button type="button" role="switch" aria-checked={trendlyneEnabled} className={trendlyneEnabled ? 'connected' : ''} onClick={() => setTrendlyneEnabled((value) => !value)}><span><i>T</i><b>Trendlyne</b><small>Research evidence</small></span><em /></button>
      </div>
    </section>
    {notice && <p className="engine-note" role="status">{notice}</p>}

    <section className="category-workspace">
      <div className="section-heading"><div><p className="eyebrow">STEP 1 · CHOOSE A SETUP</p><h3>Explore by setup</h3><p>Each setup keeps its own evidence, rejection reasons and persisted first-level intelligence run.</p></div><span>MAX 2 / CATEGORY</span></div>
      <div className="screener-categories">{categories.map((item) => <button type="button" key={item.id} className={selectedCategory === item.id ? 'selected' : ''} onClick={() => { setSelectedCategory(item.id); setNotice(''); }}><i>{item.icon}</i><span><b>{item.label}</b><small>{item.shortLabel}</small></span><em>{selectedCategory === item.id ? categoryCandidates.length : 0}</em></button>)}</div>

      <div className="selected-screener"><div><span className={`direction ${category.direction.toLowerCase()}`}>{category.direction}</span><h3>{category.label}</h3><p>{category.description}</p></div><small>Nifty 500 · {timeframe}m · Top {recommendationLimit} qualified</small></div>

      <div className="screener-controls">
        <label>Universe<select disabled><option>Nifty 500 · NSE EQ</option></select></label>
        <label>Timeframe<select value={timeframe} onChange={(event) => setTimeframe(event.target.value)}><option value="1">1 minute</option><option value="5">5 minutes</option><option value="15">15 minutes</option></select></label>
        <label>Recommendations<select value={recommendationLimit} onChange={(event) => setRecommendationLimit(event.target.value)}><option value="1">Best 1 stock</option><option value="2">Best 2 stocks</option></select></label>
        <button className="start-engine" type="button" disabled={busy} onClick={() => void getStocks()}>{busy ? 'Running intelligence…' : fyers?.connected ? '⌕ Get qualified stocks' : 'Connect FYERS to get stocks'}</button>
      </div>
    </section>

    <section className="qualified-panel">
      <div className="section-heading"><div><p className="eyebrow">STEP 2 · FIRST-LEVEL INTELLIGENCE</p><h3>Qualified stocks</h3><p>Level-one AI and all enabled backend intelligence modules run for every qualified stock. The complete run and canonical evidence copy are persisted.</p></div><span>LEVEL 1 AI</span></div>
      {categoryCandidates.length === 0 ? <div className="screener-empty"><i>{category.icon}</i><div><b>No qualified {category.label.toLowerCase()} stocks</b><p>Choose the setup and scan controls above, connect FYERS, then select Get qualified stocks.</p></div></div> : <div className="qualified-list">{categoryCandidates.map((candidate) => {
        const selected = selectedSymbols.includes(candidate.symbol);
        const ai = aiCandidates.get(candidate.symbol);
        return <article key={candidate.symbol} className={selected ? 'selected' : ''}><div className="qualified-summary"><span>#{candidate.rank}</span><div><h4>{candidate.symbol}</h4><small>₹{Number(candidate.lastPrice).toLocaleString('en-IN')} · {candidate.percentChange.toFixed(2)}%</small></div><strong>{candidate.score.toFixed(1)}</strong><em className={candidate.stance === 'LONG_WATCH' ? 'long' : 'short'}>{candidate.stance === 'LONG_WATCH' ? 'LONG' : 'SHORT'}</em><button type="button" onClick={() => setExpandedSymbol(expandedSymbol === candidate.symbol ? undefined : candidate.symbol)}>{expandedSymbol === candidate.symbol ? 'Hide analysis' : 'View level 1 AI'}</button><button type="button" className="promote" disabled={busy} onClick={() => void toggleConsolidated(candidate.symbol)}>{selected ? 'Remove from consolidated' : 'Add to consolidated'}</button></div>{expandedSymbol === candidate.symbol && ai && <MarketScreenDecisionCard candidate={ai} facts={facts.get(candidate.symbol)} ranking={rankings.get(candidate.symbol)} screenType={result!.deterministic.screenType} capital={100000} riskPercent={0.5} />}</article>;
      })}</div>}
    </section>

    <section className="consolidated-panel">
      <div className="section-heading"><div><p className="eyebrow">STEP 3 · CONSOLIDATED DECISION</p><h3>Consolidated Intraday Opportunities</h3><p>Only stocks explicitly promoted from Qualified stocks appear here. All backend intelligence modules rerun, level-one AI is refreshed, and level-two AI will add final review when enabled.</p></div><span>MAX 5</span></div>
      {consolidatedCandidates.length === 0 ? <div className="screener-empty"><i>◎</i><div><b>No consolidated stocks selected</b><p>Select a qualified stock to trigger its consolidated intelligence workflow.</p></div></div> : <div className="consolidated-list">{consolidatedCandidates.map((candidate) => {
        const run = consolidatedRuns[candidate.symbol];
        const ai = run?.ollama.candidates.find((value) => value.symbol === candidate.symbol);
        const fact = run?.deterministic.analyses.find((value) => value.assessment.symbol === candidate.symbol);
        const ranking = run?.deterministic.ranking.candidates.find((value) => value.symbol === candidate.symbol);
        return <article key={candidate.symbol}><div><b>{candidate.symbol}</b><span>{category.label}</span></div><strong>{candidate.score.toFixed(1)}</strong><ul><li className="complete">Level 1 AI · final decision active</li><li>Level 2 AI · not enabled</li><li className="complete">Backend intelligence · completed and persisted</li></ul><button type="button" onClick={() => setExpandedSymbol(expandedSymbol === candidate.symbol ? undefined : candidate.symbol)}>{expandedSymbol === candidate.symbol ? 'Hide consolidated analysis' : 'Open consolidated analysis'}</button>{expandedSymbol === candidate.symbol && ai && run && <MarketScreenDecisionCard candidate={ai} facts={fact} ranking={ranking} screenType={run.deterministic.screenType} capital={100000} riskPercent={0.5} />}</article>;
      })}</div>}
      <p className="level-rule">Until level-two AI is enabled, the refreshed level-one AI agent remains the final authority for each consolidated intraday stock.</p>
    </section>

    <div className="freshness-rule">ⓘ Discovery evidence is not an execution instruction. Timestamps, provider lineage, backend module outcomes and rejection reasons remain part of the persisted analysis record.</div>
  </section>;
}
