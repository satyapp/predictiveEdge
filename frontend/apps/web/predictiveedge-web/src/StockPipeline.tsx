import { useEffect, useMemo, useState } from 'react';
import { AuthSession } from './auth';
import { RecommendationDraft, sendStageOne } from './fyersAutomate';
import {
  domainLabels, LevelTwoVerdict, loadPipeline, PipelineOverview, pipelineLevel, QualifiedStock, runKindLabels, runLevelTwo,
} from './stockPipeline';
import './stock-pipeline.css';

type Filter = 'ALL' | 'QUALIFIED' | 'LEVEL_TWO' | 'NEEDS_LEVEL_TWO';
const filters: { id: Filter; label: string }[] = [
  { id: 'ALL', label: 'All analysed' }, { id: 'QUALIFIED', label: 'Level 1 qualified' },
  { id: 'NEEDS_LEVEL_TWO', label: 'Ready for level 2' }, { id: 'LEVEL_TWO', label: 'Level 2 done' },
];

const money = (value?: number | null) => value == null ? '—' : `₹${Number(value).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
const when = (value: string) => new Date(value).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });

function stanceLabel(stance: string) {
  if (stance === 'LONG_WATCH') return { text: 'Long', css: 'long' };
  if (stance === 'SHORT_WATCH') return { text: 'Short', css: 'short' };
  return { text: stance.replaceAll('_', ' ').toLowerCase(), css: 'neutral' };
}

/** Builds the FYERS Automate stage-1 draft from level 2 if present, else from the level-1 setup. */
export function draftFor(stock: QualifiedStock): RecommendationDraft | undefined {
  const v = stock.levelTwo;
  if (v && (v.action === 'BUY' || v.action === 'SELL') && v.stopLoss != null) {
    return { action: v.action, entryLow: v.entryLow, entryHigh: v.entryHigh, stopLoss: v.stopLoss, targetOne: v.targetOne,
      targetTwo: v.targetTwo, confidence: v.confidence, rationale: `Level 2 AI (${v.model}): ${v.rationale}` };
  }
  if (!stock.qualified || stock.referenceEntry == null || stock.indicativeStop == null) return undefined;
  return { action: stock.stance === 'SHORT_WATCH' ? 'SELL' : 'BUY', entryLow: stock.referenceEntry, entryHigh: stock.referenceEntry,
    stopLoss: stock.indicativeStop, targetOne: stock.targetOne, targetTwo: stock.targetTwo, confidence: null,
    rationale: `Level 1 AI (${stock.levelOneModel}): ${stock.thesis}`.trim() };
}

export function StockPipeline({ session, onOpenScreener, onOpenIntelligence, onOpenAutomateReview }: {
  session: AuthSession; onOpenScreener: () => void; onOpenIntelligence: () => void; onOpenAutomateReview: (reviewId: string) => void;
}) {
  const [overview, setOverview] = useState<PipelineOverview>();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('ALL');
  const [expanded, setExpanded] = useState<string>();
  const [busy, setBusy] = useState<string>();
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function refresh() {
    try { setOverview(await loadPipeline(session.accessToken)); setError(''); }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Qualified stocks could not be loaded.'); }
  }
  useEffect(() => { void refresh(); }, [session.accessToken]);

  const stocks = overview?.stocks ?? [];
  const counts = useMemo(() => ({
    ALL: stocks.length, QUALIFIED: stocks.filter((s) => s.qualified).length,
    LEVEL_TWO: stocks.filter((s) => s.levelTwo).length, NEEDS_LEVEL_TWO: stocks.filter((s) => s.qualified && !s.levelTwo).length,
  }), [stocks]);
  const visible = stocks.filter((s) => {
    const q = query.trim().toUpperCase();
    if (q && !s.symbol.includes(q) && !s.thesis.toUpperCase().includes(q)) return false;
    if (filter === 'QUALIFIED') return s.qualified;
    if (filter === 'LEVEL_TWO') return !!s.levelTwo;
    if (filter === 'NEEDS_LEVEL_TWO') return s.qualified && !s.levelTwo;
    return true;
  });

  async function levelTwo(stock: QualifiedStock) {
    setBusy(stock.symbol); setNotice(''); setError('');
    try {
      const verdict = await runLevelTwo(session.accessToken, stock.symbol, stock.runId);
      setOverview((current) => current && { ...current, stocks: current.stocks.map((s) => s.symbol === stock.symbol ? { ...s, levelTwo: verdict } : s) });
      setExpanded(stock.symbol);
      setNotice(`Level 2 AI finished for ${stock.symbol}: ${verdict.action}${verdict.confidence != null ? ` (${verdict.confidence}% confidence)` : ''}.`);
    } catch (failure) { setError(failure instanceof Error ? failure.message : `Level 2 AI failed for ${stock.symbol}.`); }
    finally { setBusy(undefined); }
  }

  async function sendToAutomate(stock: QualifiedStock) {
    const draft = draftFor(stock);
    if (!draft) return;
    setBusy(stock.symbol); setNotice(''); setError('');
    try {
      const sent = await sendStageOne(session.accessToken, stock.symbol, `pipeline:${stock.levelTwo ? 'level-two' : 'level-one'}:${stock.runId}`, draft);
      setNotice(!sent.webhook ? `${stock.symbol} saved for FIA review. Add a FYERS Automate webhook to send it.`
        : sent.delivered ? `${stock.symbol} sent to FYERS Automate. No order was placed.`
        : `${stock.symbol} saved, but FYERS Automate did not accept it: ${sent.error}`);
      onOpenAutomateReview(sent.review.reviewId);
    } catch (failure) { setError(failure instanceof Error ? failure.message : `${stock.symbol} could not be sent.`); }
    finally { setBusy(undefined); }
  }

  const searched = query.trim().toUpperCase();
  return <section className="stock-pipeline">
    <div className="section-heading">
      <div><p className="eyebrow">STOCK PIPELINE</p><h2 className="intraday-title">Screen, qualify and analyse in one place</h2>
        <p className="engine-note">Every stock your level 1 AI has analysed, from pre-open CSVs, market screens and the intraday screener, with the latest result per stock. Start level 2 from here, then send the call to FYERS Automate for FIA review. Nothing here places an order.</p></div>
    </div>

    <ol className="pipeline-steps" aria-label="Pipeline stages">
      <li><b>1 · Screen</b><span>Find candidates</span><div><button type="button" onClick={onOpenScreener}>Intraday screener</button><button type="button" onClick={onOpenIntelligence}>Market Intelligence</button></div></li>
      <li><b>2 · Level 1 AI</b><span>{counts.QUALIFIED} qualified of {counts.ALL}</span></li>
      <li><b>3 · Level 2 AI</b><span>{counts.LEVEL_TWO} reviewed · {overview ? (overview.levelTwoAvailable ? overview.levelTwoModel : 'AI provider not configured') : '…'}</span></li>
      <li><b>4 · FYERS Automate</b><span>FIA review, you confirm orders in FYERS</span></li>
    </ol>

    <div className="pipeline-toolbar">
      <input type="search" placeholder="Search a stock, e.g. SBIN" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search stocks" />
      <div className="pipeline-filters" role="tablist">{filters.map((f) =>
        <button key={f.id} type="button" role="tab" aria-selected={filter === f.id} className={filter === f.id ? 'active' : ''} onClick={() => setFilter(f.id)}>{f.label} <small>{counts[f.id]}</small></button>)}</div>
      <button type="button" className="secondary" onClick={() => void refresh()}>Refresh</button>
    </div>

    {notice && <p className="notice" role="status">{notice}</p>}
    {error && <p className="error" role="alert">{error}</p>}
    {!overview && !error && <p className="engine-note">Loading analysed stocks…</p>}
    {overview && visible.length === 0 && <div className="pipeline-empty">
      {searched ? <p><b>{searched}</b> has not been analysed by level 1 AI yet. Run it through a screener first, and it will appear here.</p>
        : <p>No stocks match this view yet. Run a screener or upload a CSV in Market Intelligence.</p>}
      <div><button type="button" onClick={onOpenScreener}>Open intraday screener</button><button type="button" onClick={onOpenIntelligence}>Open Market Intelligence</button></div>
    </div>}

    <div className="pipeline-list">{visible.map((stock) => {
      const stance = stanceLabel(stock.stance);
      const level = pipelineLevel(stock);
      const open = expanded === stock.symbol;
      const draft = draftFor(stock);
      const available = Object.values(stock.evidence).filter((v) => v === 'AVAILABLE').length;
      return <article key={stock.symbol} className={`pipeline-row ${open ? 'open' : ''}`}>
        <div className="pipeline-summary">
          <div className="pipeline-symbol"><h3>{stock.symbol}</h3><small>{runKindLabels[stock.runKind] ?? stock.runKind} · {when(stock.analyzedAt)}{stock.runCount > 1 ? ` · seen in ${stock.runCount} runs` : ''}</small></div>
          <div className="pipeline-price"><b>{money(stock.lastPrice)}</b>{stock.percentChange != null && <small className={stock.percentChange >= 0 ? 'up' : 'down'}>{stock.percentChange >= 0 ? '+' : ''}{Number(stock.percentChange).toFixed(2)}%</small>}</div>
          <em className={`stance ${stance.css}`}>{stance.text}</em>
          <div className="pipeline-score">{stock.score != null ? <><b>{Number(stock.score).toFixed(1)}</b><small>{stock.scoreBand ?? 'score'}</small></> : <small>no score</small>}</div>
          <div className="pipeline-levels">
            <span className={`badge ${stock.qualified ? 'done' : 'muted'}`}>L1 {stock.qualified ? 'qualified' : 'not qualified'}</span>
            <span className={`badge ${level === 'LEVEL_TWO' ? 'done' : 'muted'}`}>L2 {stock.levelTwo ? `${stock.levelTwo.action}${stock.levelTwo.confidence != null ? ` ${stock.levelTwo.confidence}%` : ''}` : 'not run'}</span>
            <span className="badge evidence" title="Intelligence domains with evidence">{available}/{Object.keys(stock.evidence).length || 9} evidence</span>
          </div>
          <div className="pipeline-actions">
            <button type="button" onClick={() => setExpanded(open ? undefined : stock.symbol)}>{open ? 'Hide' : 'Details'}</button>
            <button type="button" className="primary" disabled={!!busy || !overview?.levelTwoAvailable}
              title={overview?.levelTwoAvailable ? '' : 'Configure an AI provider to run level 2'}
              onClick={() => void levelTwo(stock)}>{busy === stock.symbol ? 'Working…' : stock.levelTwo ? 'Re-run level 2' : 'Run level 2'}</button>
            <button type="button" disabled={!!busy || !draft} title={draft ? '' : 'Needs a directional call with entry and stop'}
              onClick={() => void sendToAutomate(stock)}>Send to FYERS Automate</button>
          </div>
        </div>
        {open && <StockDetail stock={stock} />}
      </article>;
    })}</div>
  </section>;
}

function StockDetail({ stock }: { stock: QualifiedStock }) {
  return <div className="pipeline-detail">
    <section>
      <h4>Level 1 AI <small>{stock.levelOneModel}</small></h4>
      {stock.thesis && <p><b>Thesis.</b> {stock.thesis}</p>}
      {stock.confirmation && <p><b>Confirmation.</b> {stock.confirmation}</p>}
      {stock.risk && <p><b>Risk.</b> {stock.risk}</p>}
      <dl className="pipeline-levels-grid">
        <div><dt>Setup</dt><dd>{stock.setupAction?.replaceAll('_', ' ') ?? '—'}</dd></div>
        <div><dt>Entry</dt><dd>{money(stock.referenceEntry)}</dd></div>
        <div><dt>Stop</dt><dd>{money(stock.indicativeStop)}</dd></div>
        <div><dt>Target 1</dt><dd>{money(stock.targetOne)}</dd></div>
        <div><dt>RSI 14</dt><dd>{stock.rsi14 != null ? Number(stock.rsi14).toFixed(1) : '—'}</dd></div>
        <div><dt>Rule</dt><dd>{stock.confirmationRule ?? '—'}</dd></div>
      </dl>
      <h5>Intelligence evidence</h5>
      <div className="evidence-chips">{Object.entries(stock.evidence).map(([domain, status]) =>
        <span key={domain} className={status === 'AVAILABLE' ? 'on' : 'off'}>{domainLabels[domain] ?? domain}</span>)}
        {Object.keys(stock.evidence).length === 0 && <span className="off">No evidence was saved with this run</span>}</div>
    </section>
    {stock.levelTwo ? <LevelTwoCard verdict={stock.levelTwo} /> : <section className="level-two-empty"><h4>Level 2 AI</h4>
      <p>Not run yet. Level 2 re-reads the saved evidence (technical, fundamental, news and sentiment, market conditions, flows and corporate actions) and returns its own call with levels.</p></section>}
  </div>;
}

function LevelTwoCard({ verdict }: { verdict: LevelTwoVerdict }) {
  return <section className="level-two-card">
    <h4>Level 2 AI <small>{verdict.model} · {when(verdict.createdAt)}</small></h4>
    <div className="level-two-call"><em className={`action ${verdict.action.toLowerCase()}`}>{verdict.action}</em>
      {verdict.confidence != null && <b>{verdict.confidence}% confidence</b>}{verdict.horizon && <small>{verdict.horizon.toLowerCase()}</small>}</div>
    <dl className="pipeline-levels-grid">
      <div><dt>Entry</dt><dd>{verdict.entryLow != null ? `${money(verdict.entryLow)}${verdict.entryHigh != null && verdict.entryHigh !== verdict.entryLow ? `–${money(verdict.entryHigh)}` : ''}` : '—'}</dd></div>
      <div><dt>Stop</dt><dd>{money(verdict.stopLoss)}</dd></div>
      <div><dt>Target 1</dt><dd>{money(verdict.targetOne)}</dd></div>
      <div><dt>Target 2</dt><dd>{money(verdict.targetTwo)}</dd></div>
    </dl>
    <ul className="domain-views">{Object.entries(verdict.domains ?? {}).map(([domain, view]) =>
      <li key={domain}><span className={`view ${view.view.toLowerCase()}`}>{view.view.replaceAll('_', ' ').toLowerCase()}</span><b>{domainLabels[domain] ?? domain}</b>{view.note && <small>{view.note}</small>}</li>)}</ul>
    {verdict.rationale && <p><b>Rationale.</b> {verdict.rationale}</p>}
    {verdict.keyRisks?.length > 0 && <p><b>Key risks.</b> {verdict.keyRisks.join('; ')}</p>}
    {verdict.validationNotes?.length > 0 && <p className="validation-notes"><b>Checks applied.</b> {verdict.validationNotes.join(' ')}</p>}
  </section>;
}
