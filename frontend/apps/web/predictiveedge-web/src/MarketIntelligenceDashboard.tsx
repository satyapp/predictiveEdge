import { FormEvent, useMemo, useState } from 'react';
import { AuthSession } from './auth';
import { BrokerOverview } from './broker';
import { analyzeMarketScreenCsv, MarketScreenAnalysisResponse, MarketScreenType } from './marketScreens';
import { analyzePreOpenCsv, PreOpenAnalysisResponse } from './preopen';
import { MarketScreenDecisionCard, PreOpenDecisionCard } from './PreOpenDecisionCard';
import { AnalysisHistorySummary, loadAnalysisHistory, loadAnalysisHistoryDetail } from './analysisHistory';

type AnalysisCategory = 'PRE_OPEN' | MarketScreenType;
type ResultTab = 'CANDIDATES' | 'STAGE_ONE' | 'STAGE_TWO' | 'COMPARE';

const categories: { value: AnalysisCategory; label: string; icon: string; enabled: boolean }[] = [
  { value: 'PRE_OPEN', label: 'Pre-Open Market Data', icon: '☀', enabled: true },
  { value: 'TOP_GAINERS', label: 'Top 20 Gainers', icon: '↗', enabled: true },
  { value: 'TOP_LOSERS', label: 'Top 20 Losers', icon: '↘', enabled: true },
  { value: 'NEW_52_WEEK_HIGH', label: 'New 52-Week High / Low', icon: '⌁', enabled: false },
  { value: 'MOST_ACTIVE_EQUITIES', label: 'Most Active Equities', icon: '▥', enabled: false },
  { value: 'PRICE_BAND_HITTERS', label: 'Price Band Hitters', icon: '⬡', enabled: false },
  { value: 'VOLUME_GAINERS', label: 'Volume Gainers', icon: '▤', enabled: false },
];

export function MarketIntelligenceDashboard({ session, broker }: { session: AuthSession; broker?: BrokerOverview }) {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const [view, setView] = useState<'ANALYZE' | 'HISTORY'>('ANALYZE');
  const [category, setCategory] = useState<AnalysisCategory>('PRE_OPEN');
  const [tradingDate, setTradingDate] = useState(today);
  const [maximumCandidates, setMaximumCandidates] = useState(10);
  const [file, setFile] = useState<File>();
  const [capital, setCapital] = useState(100000);
  const [riskPercent, setRiskPercent] = useState(0.5);
  const [preOpenResult, setPreOpenResult] = useState<PreOpenAnalysisResponse>();
  const [screenResult, setScreenResult] = useState<MarketScreenAnalysisResponse>();
  const [resultTab, setResultTab] = useState<ResultTab>('CANDIDATES');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [historyItems, setHistoryItems] = useState<AnalysisHistorySummary[]>([]);
  const [historyBusy, setHistoryBusy] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [loadedFromHistory, setLoadedFromHistory] = useState(false);

  const hasResult = Boolean(preOpenResult || screenResult);
  const selectedCategory = categories.find((value) => value.value === category)!;
  const candidateCount = preOpenResult?.ollama.candidates.length ?? screenResult?.ollama.candidates.length ?? 0;
  const deterministic = useMemo(() => new Map(preOpenResult?.deterministic.analyses.map((value) => [value.assessment.symbol, value])), [preOpenResult]);
  const rankings = useMemo(() => new Map([
    ...(preOpenResult?.deterministic.ranking.probableGainers ?? []),
    ...(preOpenResult?.deterministic.ranking.probableLosers ?? []),
  ].map((value) => [value.symbol, value])), [preOpenResult]);
  const screenFacts = useMemo(() => new Map(screenResult?.deterministic.analyses.map((value) => [value.assessment.symbol, value])), [screenResult]);
  const screenRankings = useMemo(() => new Map(screenResult?.deterministic.ranking.candidates.map((value) => [value.symbol, value])), [screenResult]);
  const candidateSummaries = preOpenResult?.ollama.candidates.map((value, index) => ({
    rank: index + 1, symbol: value.symbol, stance: value.stance,
  })) ?? screenResult?.ollama.candidates.map((value, index) => ({
    rank: screenRankings.get(value.symbol)?.rank ?? index + 1, symbol: value.symbol, stance: value.stance,
  })) ?? [];

  function chooseCategory(value: AnalysisCategory) {
    setCategory(value); setFile(undefined); setPreOpenResult(undefined); setScreenResult(undefined); setError(''); setLoadedFromHistory(false);
  }

  async function showHistory() {
    setView('HISTORY'); setHistoryBusy(true); setHistoryError('');
    try { setHistoryItems(await loadAnalysisHistory(session.accessToken)); }
    catch (failure) { setHistoryError(failure instanceof Error ? failure.message : 'Analysis history is unavailable.'); }
    finally { setHistoryBusy(false); }
  }

  async function openHistory(run: AnalysisHistorySummary) {
    setHistoryBusy(true); setHistoryError('');
    try {
      const detail = await loadAnalysisHistoryDetail(session.accessToken, run.runId);
      setCategory(detail.category as AnalysisCategory); setPreOpenResult(undefined); setScreenResult(undefined);
      if (detail.resultKind === 'PRE_OPEN_FULL') setPreOpenResult(detail.result as PreOpenAnalysisResponse);
      else if (detail.resultKind === 'MARKET_SCREEN_FULL') setScreenResult(detail.result as MarketScreenAnalysisResponse);
      else throw new Error('This deterministic-only run does not contain the Stage‑1 recommendation report.');
      setTradingDate(detail.tradingDate); setView('ANALYZE'); setResultTab('STAGE_ONE'); setLoadedFromHistory(true);
    } catch (failure) { setHistoryError(failure instanceof Error ? failure.message : 'Saved analysis could not be opened.'); }
    finally { setHistoryBusy(false); }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    setBusy(true); setError(''); setPreOpenResult(undefined); setScreenResult(undefined); setResultTab('CANDIDATES'); setLoadedFromHistory(false);
    try {
      if (!broker?.zerodhaAccountId) throw new Error('Connect Zerodha before running historical analysis.');
      if (category === 'PRE_OPEN') {
        setPreOpenResult(await analyzePreOpenCsv({
          accessToken: session.accessToken, brokerAccountId: broker.zerodhaAccountId,
          tradingDate, file, maximumCandidates,
        }));
      } else {
        setScreenResult(await analyzeMarketScreenCsv({
          accessToken: session.accessToken, brokerAccountId: broker.zerodhaAccountId,
          screenType: category, tradingDate, file, maximumCandidates,
        }));
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Analysis could not be completed.');
    } finally { setBusy(false); }
  }

  return <section className="intelligence-dashboard">
    <div className="intelligence-title">
      <div><p className="eyebrow">NSE MARKET INTELLIGENCE</p><h2>NSE Analysis with Zerodha + AI <span>(Ollama → OpenAI)</span></h2><p>Screen and rank NSE opportunities with deterministic evidence and a staged AI review.</p></div>
      <span className="preliminary">PRELIMINARY</span>
    </div>

    <div className="dashboard-tabs" role="tablist">
      <button type="button" className={view === 'ANALYZE' ? 'active' : ''} onClick={() => setView('ANALYZE')}><b>▶</b><span>Analyze (New)<small>Run a new analysis on current data</small></span></button>
      <button type="button" className={view === 'HISTORY' ? 'active' : ''} onClick={showHistory}><b>▱</b><span>Historical Analysis<small>Open saved reports without rerunning analysis</small></span></button>
    </div>

    {view === 'HISTORY' ? <div className="analysis-history">
      <div className="history-heading"><div><h3>Saved analysis history</h3><p>Completed reports are loaded from PostgreSQL; opening one does not parse the CSV, call Zerodha, or run Ollama again.</p></div><button type="button" onClick={showHistory} disabled={historyBusy}>{historyBusy ? 'Refreshing…' : 'Refresh'}</button></div>
      {historyError && <p className="error" role="alert">{historyError}</p>}
      {!historyBusy && historyItems.length === 0 && !historyError && <p className="empty-history">No saved analyses yet. The next completed run will appear here.</p>}
      <div className="history-list">{historyItems.map((run) => {
        const dataOnly = run.resultKind === 'PRE_OPEN_DETERMINISTIC' || run.resultKind === 'MARKET_SCREEN';
        return <button type="button" key={run.runId} onClick={() => openHistory(run)} disabled={historyBusy || dataOnly}><span><small>TRADING DATE</small><b>{run.tradingDate}</b></span><span><small>CATEGORY</small><b>{categories.find((value) => value.value === run.category)?.label ?? run.category.replaceAll('_', ' ')}</b></span><span><small>FILE</small><b>{run.fileName}</b></span><span><small>CANDIDATES</small><b>{run.candidateCount}</b></span><span><small>MODEL</small><b>{run.stageOneModel}</b></span><i>{dataOnly ? 'DATA ONLY' : 'OPEN ›'}</i></button>;
      })}</div>
    </div> : <>
      <form className="analysis-console" onSubmit={submit}>
        <div className="analysis-inputs">
          <label>Trading date<input type="date" value={tradingDate} onChange={(event) => setTradingDate(event.target.value)} required /></label>
          <label>Maximum candidates<input type="number" min="1" max="25" value={maximumCandidates} onChange={(event) => setMaximumCandidates(Number(event.target.value))} required /></label>
          <label className="file-field">NSE Data CSV<input key={category} type="file" accept=".csv,text/csv" onChange={(event) => setFile(event.target.files?.[0])} required /><span>{file?.name ?? `Choose ${selectedCategory.label} CSV`}</span></label>
        </div>

        <div className="analysis-grid">
          <div className="category-picker"><h3>Analysis category</h3><p>Use one consistent recommendation report for every supported NSE category.</p><label><span>{selectedCategory.icon}</span><select value={category} onChange={(event) => chooseCategory(event.target.value as AnalysisCategory)}>{categories.map((item) => <option key={item.value} value={item.value} disabled={!item.enabled}>{item.label}{!item.enabled ? ' — format pending' : ''}</option>)}</select></label></div>

          <div className="analysis-options"><h3>Analysis options</h3>
            <span><i>AI</i><b>AI explanation</b><small>Ollama · Stage‑1</small><em>ON</em></span>
            <span><i>↻</i><b>Include Zerodha history</b><small>Required for every supported category</small><em className={broker?.zerodhaConnected ? '' : 'muted'}>{broker?.zerodhaConnected ? 'ON' : '—'}</em></span>
            <span><i>▱</i><b>Save result to history</b><small>Full report and source fingerprint</small><em>ON</em></span>
            <button className="run-analysis" disabled={busy || !file || !broker?.zerodhaConnected}>{busy ? 'Running Stage‑1 analysis…' : '▶ Run Stage‑1 (History + Ollama)'}</button>
          </div>

          <div className="workflow-panel"><h3>Analysis workflow</h3>
            <div className="workflow-step ready"><i>✓</i><span><b>1. Data & screening</b><small>NSE CSV + deterministic ranking</small></span></div>
            <div className={`workflow-step ${preOpenResult?.ollama.invoked || screenResult?.ollama.invoked ? 'ready' : ''}`}><i>{preOpenResult?.ollama.invoked || screenResult?.ollama.invoked ? '✓' : '2'}</i><span><b>2. AI analysis · Stage‑1</b><small>Ollama explanation and risk factors</small></span></div>
            <div className="workflow-step future"><i>3</i><span><b>3. Advanced analysis · Stage‑2</b><small>On-demand OpenAI refinement from frozen Stage‑1 evidence</small></span></div>
            <p>Stage‑2 will not query Zerodha or parse the CSV again.</p>
          </div>
        </div>
      </form>
      {error && <p className="error" role="alert">{error}</p>}

      {hasResult && <div className="unified-report">
        <div className="report-heading"><div><h3>Analysis Result — Stage‑1 Report</h3><p>{selectedCategory.label} · {candidateCount} candidates{loadedFromHistory ? ' · opened from saved history' : ' · saved to history'}</p></div><span className="completed">✓ Completed</span><div className="model-flow"><b>Ollama · Stage‑1</b><span>+</span><b className="future">OpenAI · Stage‑2</b></div></div>
        <div className="report-tabs">
          {(['CANDIDATES', 'STAGE_ONE', 'STAGE_TWO', 'COMPARE'] as ResultTab[]).map((tab) => <button type="button" key={tab} className={resultTab === tab ? 'active' : ''} disabled={(tab === 'STAGE_TWO' || tab === 'COMPARE') && true} onClick={() => setResultTab(tab)}>{tab === 'CANDIDATES' ? `Candidate List (${candidateCount})` : tab === 'STAGE_ONE' ? 'Ollama Analysis (Stage‑1)' : tab === 'STAGE_TWO' ? 'Combined Analysis (Stage‑2) · Planned' : 'Side-by-Side Compare · Planned'}</button>)}
        </div>

        {resultTab === 'CANDIDATES' && <div className="candidate-list-summary">{candidateSummaries.map((candidate) => <button type="button" key={candidate.symbol} onClick={() => setResultTab('STAGE_ONE')}><span>#{candidate.rank}</span><b>{candidate.symbol}</b><em className={candidate.stance === 'LONG_WATCH' ? 'buy' : candidate.stance === 'SHORT_WATCH' ? 'sell' : 'wait'}>{candidate.stance.replace('_', ' ')}</em><i>›</i></button>)}</div>}

        {resultTab === 'STAGE_ONE' && <>
          <div className="ollama-overview"><div><small>STAGE‑1 MODEL</small><b>{preOpenResult?.ollama.model ?? screenResult?.ollama.model}</b></div>{(preOpenResult?.ollama ?? screenResult?.ollama) && <span className={`tone ${(preOpenResult?.ollama.marketTone ?? screenResult!.ollama.marketTone).toLowerCase()}`}>{preOpenResult?.ollama.marketTone ?? screenResult?.ollama.marketTone}</span>}<p>{preOpenResult?.ollama.overview ?? screenResult?.ollama.overview}</p></div>
          <div className="analysis-meta"><span><b>{preOpenResult?.deterministic.parsedSnapshots ?? screenResult?.deterministic.parsedRows}</b> usable rows</span><span><b>{preOpenResult?.deterministic.shortlistedForHistory ?? screenResult?.deterministic.analyses.length ?? 0}</b> Zerodha histories</span><span><b>{preOpenResult?.deterministic.failures.length ?? screenResult?.deterministic.failures.length ?? 0}</b> failures</span><span><b>{candidateCount}</b> recommendations</span></div>
          <div className="risk-controls"><div><small>ILLUSTRATIVE POSITION-SIZE INPUTS</small><b>Set the maximum loss budget before reviewing a setup</b><p>Margin, slippage, correlation and portfolio exposure remain outside this calculator.</p></div><label>Capital (₹)<input type="number" min="1" step="1000" value={capital} onChange={(event) => setCapital(Number(event.target.value))} /></label><label>Risk per trade (%)<input type="number" min="0.05" max="5" step="0.05" value={riskPercent} onChange={(event) => setRiskPercent(Number(event.target.value))} /></label></div>
          <div className="decision-list">
            {preOpenResult?.ollama.candidates.map((candidate) => <PreOpenDecisionCard key={candidate.symbol} candidate={candidate} facts={deterministic.get(candidate.symbol)} ranking={rankings.get(candidate.symbol)} capital={capital} riskPercent={riskPercent} />)}
            {screenResult?.ollama.candidates.map((candidate) => <MarketScreenDecisionCard key={candidate.symbol} candidate={candidate} facts={screenFacts.get(candidate.symbol)} ranking={screenRankings.get(candidate.symbol)} screenType={screenResult.deterministic.screenType} capital={capital} riskPercent={riskPercent} />)}
          </div>
          {preOpenResult && preOpenResult.deterministic.failures.length > 0 && <div className="failure-block"><b>Historical analysis failures</b>{preOpenResult.deterministic.failures.map((value) => <div className="diagnostic-row" key={`${value.venue}:${value.symbol}`}><span>{value.symbol}</span><span>FAILED</span><span>{value.reason}</span></div>)}</div>}
          {screenResult && screenResult.deterministic.failures.length > 0 && <div className="failure-block"><b>Historical analysis failures</b>{screenResult.deterministic.failures.map((value) => <div className="diagnostic-row" key={`${value.venue}:${value.symbol}`}><span>{value.symbol}</span><span>FAILED</span><span>{value.reason}</span></div>)}</div>}
        </>}
        <div className="stage-two-callout"><div><b>Stage‑2 OpenAI refinement</b><p>Phase 2 will send the immutable Stage‑1 evidence bundle and Ollama report to OpenAI only when the user requests it. The connector layer stays out of this stage.</p></div><button type="button" disabled>Run Advanced Analysis · Phase 2</button></div>
      </div>}
    </>}
  </section>;
}
