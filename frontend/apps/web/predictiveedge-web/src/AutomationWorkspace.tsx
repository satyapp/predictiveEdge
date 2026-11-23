import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import {
  AutomationCandidate, AutomationDataSource, createAutomationDataSource, riskBudget, RiskSettings, TradeAction,
  TradePlan, tradePlan, TradeRecommendation, validRiskSettings,
} from './automation';
import './automation-workspace.css';

type Tab = 'analysis' | 'fia' | 'final' | 'backtest';

type Row = {
  recommendation: TradeRecommendation;
  selected: boolean;
  quantityOverride: number | null;
  fiaRequested: boolean;
  fiaReasoning: string;
  fiaSaved: boolean;
  finalReasoning: string;
  finalSaved: boolean;
};

type EditForm = { entry: string; stopLoss: string; target1: string; target2: string; quantity: string; confidence: string; action: TradeAction; reasoning: string };

const tabs: { id: Tab; label: string }[] = [
  { id: 'analysis', label: 'Level 2 AI' },
  { id: 'fia', label: 'FIA Review' },
  { id: 'final', label: 'Final Review' },
  { id: 'backtest', label: 'Backtesting' },
];

const rupees = (value: number) => `₹${Math.round(value).toLocaleString('en-IN')}`;
const price = (value: number) => `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const istDate = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const displayDate = (isoDate: string) => new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${isoDate}T00:00:00Z`));

function toRow(recommendation: TradeRecommendation): Row {
  return { recommendation, selected: true, quantityOverride: null, fiaRequested: false, fiaReasoning: '', fiaSaved: false, finalReasoning: '', finalSaved: false };
}

function tradeSummary(recommendation: TradeRecommendation, plan: TradePlan) {
  return `${recommendation.action} · Entry ${price(recommendation.entry)} · Stop ${price(recommendation.stopLoss)} · T1 ${price(recommendation.target1)} · T2 ${price(recommendation.target2)} · Qty ${plan.quantity} · Planned risk ${rupees(plan.plannedLoss)}`;
}

export function AutomationWorkspace({ candidates, dataSource: providedSource, onOpenStockSearch }: {
  candidates?: AutomationCandidate[];
  dataSource?: AutomationDataSource;
  onOpenStockSearch?: () => void;
}) {
  const dataSource = useMemo(() => providedSource ?? createAutomationDataSource(), [providedSource]);
  const [tradingDate] = useState(istDate);
  const [capitalInput, setCapitalInput] = useState('100000');
  const [riskInput, setRiskInput] = useState('1');
  const [rows, setRows] = useState<Row[]>([]);
  const [activeSymbol, setActiveSymbol] = useState('');
  const [tab, setTab] = useState<Tab>('analysis');
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');
  const [fiaDraft, setFiaDraft] = useState('');
  const [finalDraft, setFinalDraft] = useState('');
  const [editing, setEditing] = useState<string>();
  const [editForm, setEditForm] = useState<EditForm>();
  const [editError, setEditError] = useState('');
  const reasoningPanel = useRef<HTMLElement>(null);

  const settings: RiskSettings = { capital: Math.max(0, Number(capitalInput) || 0), riskPercent: Math.max(0, Number(riskInput) || 0) };

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    dataSource.loadRecommendations({ tradingDate, candidates })
      .then((recommendations) => {
        if (!mounted) return;
        setRows(recommendations.map(toRow));
        setActiveSymbol(recommendations[0]?.symbol ?? '');
      })
      .catch((failure) => mounted && setNotice(failure instanceof Error ? failure.message : 'Recommendations could not be loaded.'))
      .finally(() => mounted && setLoading(false));
    return () => { mounted = false; };
  }, [candidates, dataSource, tradingDate]);

  const active = rows.find((row) => row.recommendation.symbol === activeSymbol);
  const activePlan = active ? tradePlan(active.recommendation, settings, active.quantityOverride) : undefined;

  // Keep the review text boxes in sync with the stock being reviewed.
  useEffect(() => { setFiaDraft(active?.fiaReasoning ?? ''); setFinalDraft(active?.finalReasoning ?? ''); }, [activeSymbol, active?.fiaReasoning, active?.finalReasoning]);

  const selectedRows = rows.filter((row) => row.selected);
  const plans = selectedRows.map((row) => tradePlan(row.recommendation, settings, row.quantityOverride));
  const capitalUsed = plans.reduce((total, plan) => total + plan.capitalRequired, 0);
  const plannedLoss = plans.reduce((total, plan) => total + plan.plannedLoss, 0);
  const expectedProfit = plans.reduce((total, plan) => total + plan.expectedProfit, 0);
  const budget = riskBudget(settings);
  const portfolioWarning = capitalUsed > settings.capital
    ? 'Selected positions exceed available capital. Reduce quantities before approving trades.'
    : plannedLoss > budget ? 'Combined planned risk exceeds the one-trade risk budget. Apply a separate portfolio risk limit.' : '';

  function updateRow(symbol: string, change: (row: Row) => Row) {
    setRows((current) => current.map((row) => row.recommendation.symbol === symbol ? change(row) : row));
  }

  function changeRisk(setter: (value: string) => void, value: string) {
    setter(value);
    // Sizing inputs changed: quantity overrides and final decisions are no longer valid.
    setRows((current) => current.map((row) => ({ ...row, quantityOverride: null, finalSaved: false, finalReasoning: '' })));
  }

  function viewReasoning(symbol: string) {
    setActiveSymbol(symbol); setTab('analysis');
    window.requestAnimationFrame(() => reasoningPanel.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
  }

  async function requestFia(symbol = activeSymbol) {
    const row = rows.find((item) => item.recommendation.symbol === symbol);
    if (!row) return;
    setActiveSymbol(symbol); setTab('fia');
    updateRow(symbol, (current) => ({ ...current, fiaRequested: true }));
    try { setNotice((await dataSource.requestFiaReview({ recommendation: row.recommendation, plan: tradePlan(row.recommendation, settings, row.quantityOverride) })).message); }
    catch (failure) { setNotice(failure instanceof Error ? failure.message : 'FIA review could not be requested.'); }
  }

  async function saveFia() {
    const reasoning = fiaDraft.trim();
    if (!active) return;
    if (!reasoning) return setNotice('Enter the FIA reasoning before saving.');
    try {
      await dataSource.saveFiaReview({ tradingDate, symbol: active.recommendation.symbol, reasoning });
      updateRow(active.recommendation.symbol, (row) => ({ ...row, fiaReasoning: reasoning, fiaSaved: true, finalSaved: false, finalReasoning: '' }));
      setNotice(`FIA review saved for ${active.recommendation.symbol}.`);
    } catch (failure) { setNotice(failure instanceof Error ? failure.message : 'FIA review could not be saved.'); }
  }

  async function saveFinal() {
    const reasoning = finalDraft.trim();
    if (!active) return;
    if (!active.fiaSaved) return setNotice('Save the FIA review first.');
    if (!reasoning) return setNotice('Enter the final assessment before saving.');
    try {
      await dataSource.saveFinalReview({ tradingDate, symbol: active.recommendation.symbol, reasoning });
      updateRow(active.recommendation.symbol, (row) => ({ ...row, finalReasoning: reasoning, finalSaved: true }));
      setNotice(`Final recommendation saved for ${active.recommendation.symbol}.`);
    } catch (failure) { setNotice(failure instanceof Error ? failure.message : 'Final recommendation could not be saved.'); }
  }

  async function submitBacktest() {
    if (!active || !activePlan) return;
    try { setNotice((await dataSource.submitBacktest({ recommendation: active.recommendation, plan: activePlan, settings })).message); }
    catch (failure) { setNotice(failure instanceof Error ? failure.message : 'Backtest could not be submitted.'); }
  }

  function openEdit(symbol: string) {
    const row = rows.find((item) => item.recommendation.symbol === symbol);
    if (!row) return;
    const { recommendation } = row;
    setEditing(symbol); setEditError('');
    setEditForm({ entry: String(recommendation.entry), stopLoss: String(recommendation.stopLoss), target1: String(recommendation.target1), target2: String(recommendation.target2), quantity: String(tradePlan(recommendation, settings, row.quantityOverride).quantity), confidence: String(recommendation.confidence), action: recommendation.action, reasoning: recommendation.reasoning });
  }

  function saveEdit(event: FormEvent) {
    event.preventDefault();
    const row = rows.find((item) => item.recommendation.symbol === editing);
    if (!row || !editForm) return;
    const draft: TradeRecommendation = { ...row.recommendation, entry: Number(editForm.entry), stopLoss: Number(editForm.stopLoss), target1: Number(editForm.target1), target2: Number(editForm.target2), confidence: Number(editForm.confidence), action: editForm.action, reasoning: editForm.reasoning };
    const quantity = Number(editForm.quantity);
    const plan = tradePlan(draft, settings);
    if (!plan.valid) return setEditError(`Invalid entry, stop loss or target order for a ${draft.action} trade.`);
    if (!Number.isInteger(quantity) || quantity < 0 || quantity > plan.suggestedQuantity) return setEditError(`Quantity must be a whole number between 0 and ${plan.suggestedQuantity} for the current capital and risk.`);
    if (!(draft.confidence >= 0 && draft.confidence <= 100)) return setEditError('Confidence must be between 0 and 100.');
    updateRow(row.recommendation.symbol, () => ({ ...row, recommendation: draft, quantityOverride: quantity === plan.suggestedQuantity ? null : quantity, fiaRequested: false, fiaReasoning: '', fiaSaved: false, finalReasoning: '', finalSaved: false }));
    setEditing(undefined);
    setNotice(`${draft.symbol} updated. Its FIA and final reviews were cleared.`);
  }

  const stockPicker = <select aria-label="Stock under review" value={activeSymbol} onChange={(event) => setActiveSymbol(event.target.value)}>{rows.map((row) => <option key={row.recommendation.symbol}>{row.recommendation.symbol}</option>)}</select>;
  const fromIntradayList = Boolean(candidates?.length);
  const backtestBlocked = !active?.finalSaved || !activePlan?.valid || capitalUsed > settings.capital || !validRiskSettings(settings);

  return <div className="automation-workspace-page">
    <section className="aw-title">
      <div><span>AI TRADE AUTOMATION</span><h1>Automation Workspace</h1><p>Intraday equity · {displayDate(tradingDate)} · Size, review and backtest Level 2 AI trade ideas before any order.</p></div>
      <div className="aw-title-badges"><em className="aw-demo">{dataSource.live ? 'LIVE DATA' : 'DEMO · NO LIVE ORDERS'}</em><em className="aw-source">{fromIntradayList ? `From Intraday List · ${candidates!.length} stocks` : 'Sample recommendations'}</em></div>
    </section>

    {!fromIntradayList && <div className="aw-hint"><span>ⓘ</span><p>Showing sample recommendations. Add stocks to today's Intraday List on Stock Search and press <b>Automate</b> to review them here.</p>{onOpenStockSearch && <button type="button" onClick={onOpenStockSearch}>Open Stock Search</button>}</div>}

    <section className="aw-card">
      <header><span>₹</span><h2>Capital &amp; Risk Configuration</h2></header>
      <div className="aw-risk">
        <label>Total Capital (₹)<input type="number" min="1" value={capitalInput} onChange={(event) => changeRisk(setCapitalInput, event.target.value)} /></label>
        <label>Risk Factor (%) per trade<input type="number" min="0.01" max="100" step="0.1" value={riskInput} onChange={(event) => changeRisk(setRiskInput, event.target.value)} /></label>
      </div>
      <p className="aw-help">Position size = floor(min(risk budget ÷ stop distance, capital ÷ entry price)). This is a per-trade calculation, not a portfolio allocator.{!validRiskSettings(settings) && <b> Enter a capital above zero and a risk factor between 0 and 100%.</b>}</p>
    </section>

    <section className="aw-stats" aria-label="Automation summary">
      <article><small>RISK BUDGET / TRADE</small><strong>{rupees(budget)}</strong></article>
      <article><small>CAPITAL REQUIRED</small><strong className={capitalUsed > settings.capital ? 'negative' : ''}>{rupees(capitalUsed)}</strong></article>
      <article><small>PLANNED STOP RISK</small><strong>{rupees(plannedLoss)}</strong></article>
      <article><small>EXPECTED PROFIT (T1)</small><strong className="positive">{rupees(expectedProfit)}</strong></article>
    </section>
    {portfolioWarning && <p className="aw-warning" role="alert">⚠ {portfolioWarning}</p>}

    <nav className="aw-tabs" role="tablist" aria-label="Automation review steps">{tabs.map((item, index) => <button key={item.id} type="button" role="tab" aria-selected={tab === item.id} className={tab === item.id ? 'active' : ''} onClick={() => setTab(item.id)}><i>{index + 1}</i>{item.label}</button>)}</nav>

    {tab === 'analysis' && <>
      <section className="aw-card">
        <header><span>✦</span><h2>Level 2 AI Recommendations</h2><label className="aw-select-all"><input type="checkbox" checked={rows.length > 0 && rows.every((row) => row.selected)} onChange={(event) => setRows((current) => current.map((row) => ({ ...row, selected: event.target.checked })))} /> Select all</label></header>
        <div className="aw-scroll"><table className="aw-table"><thead><tr><th>Select / Symbol</th><th>Date / Category</th><th>Rank / Confidence</th><th>AI Reasoning</th><th>Action / Entry</th><th>Stop Loss</th><th>Targets 1 / 2</th><th>Quantity</th><th>Expected Profit / Risk</th><th>R:R</th><th>Actions</th></tr></thead>
          <tbody>{rows.map((row) => { const r = row.recommendation; const plan = tradePlan(r, settings, row.quantityOverride); return <tr key={r.symbol} className={r.symbol === activeSymbol ? 'active' : ''}>
            <td><label className="aw-symbol"><input type="checkbox" checked={row.selected} onChange={(event) => updateRow(r.symbol, (current) => ({ ...current, selected: event.target.checked }))} /><b>{r.symbol}</b></label><Stage row={row} /></td>
            <td>{displayDate(r.tradingDate)}<small>{r.category}</small></td>
            <td><span className="aw-rank">#{r.rank}</span><small>{r.confidence}% confidence</small></td>
            <td><button type="button" className="aw-link" onClick={() => viewReasoning(r.symbol)}>View reasoning</button></td>
            <td><span className={`aw-action ${r.action.toLowerCase()}`}>{r.action}</span><small>{price(r.entry)}</small></td>
            <td>{price(r.stopLoss)}</td>
            <td>{price(r.target1)}<small>{price(r.target2)}</small></td>
            <td><b>{plan.quantity}</b>{row.quantityOverride !== null && <small>edited</small>}</td>
            <td><span className="positive">+{rupees(plan.expectedProfit)}</span><small className="negative">−{rupees(plan.plannedLoss)}</small></td>
            <td>{plan.rewardToRisk === undefined ? '—' : `${plan.rewardToRisk.toFixed(2)}:1`}</td>
            <td><div className="aw-row-actions"><button type="button" className="aw-secondary" onClick={() => openEdit(r.symbol)}>Edit</button><button type="button" className="aw-primary" onClick={() => void requestFia(r.symbol)}>FIA Review</button></div></td>
          </tr>; })}
          {rows.length === 0 && <tr className="aw-empty"><td colSpan={11}>{loading ? 'Loading recommendations…' : 'No recommendations for this trading day.'}</td></tr>}</tbody></table></div>
      </section>
      <section ref={reasoningPanel} className="aw-card">
        <header><span>☰</span><h2>Level 2 AI Reasoning</h2>{stockPicker}</header>
        <p className="aw-body">{active?.recommendation.reasoning ?? 'Select a stock to read its reasoning.'}</p>
      </section>
    </>}

    {tab === 'fia' && active && activePlan && <section className="aw-card">
      <header><span>F</span><h2>FYERS FIA Review</h2>{stockPicker}</header>
      <div className="aw-panel-body">
        <div className="aw-setup"><b>{active.recommendation.symbol}</b><p>{tradeSummary(active.recommendation, activePlan)}</p><p className="aw-muted">AI reasoning: {active.recommendation.reasoning}</p></div>
        <button type="button" className="aw-secondary" onClick={() => void requestFia()}>{active.fiaRequested ? 'Request FIA Review again' : 'Request FIA Review'}</button>
        <label className="aw-field">FIA reasoning<textarea value={fiaDraft} onChange={(event) => setFiaDraft(event.target.value)} placeholder="Paste FYERS FIA reasoning here. A future authorised webhook will fill this automatically." /></label>
        <div className="aw-buttons"><button type="button" className="aw-primary" onClick={() => void saveFia()}>Save FIA Review</button><button type="button" className="aw-secondary" disabled={!active.fiaSaved} onClick={() => setTab('final')}>Final Review →</button></div>
      </div>
    </section>}

    {tab === 'final' && active && activePlan && <section className="aw-card">
      <header><span>✓</span><h2>Final Review / Recommendation</h2>{stockPicker}</header>
      <div className="aw-panel-body">
        {active.fiaSaved
          ? <div className="aw-compare"><div><small>LEVEL 2 AI</small><p>{active.recommendation.reasoning}</p></div><div><small>FIA REVIEW</small><p>{active.fiaReasoning}</p></div><div className="wide"><small>TRADE</small><p>{tradeSummary(active.recommendation, activePlan)} · Expected profit {rupees(activePlan.expectedProfit)}</p></div></div>
          : <p className="aw-blocked">Save the FIA review for {active.recommendation.symbol} before the final review.</p>}
        <label className="aw-field">Final assessment / resolution<textarea value={finalDraft} disabled={!active.fiaSaved} onChange={(event) => setFinalDraft(event.target.value)} placeholder="Compare the Level 2 AI and FIA reasoning; record the final decision or NO TRADE." /></label>
        <div className="aw-buttons"><button type="button" className="aw-primary" disabled={!active.fiaSaved} onClick={() => void saveFinal()}>Save Final Recommendation</button><button type="button" className="aw-secondary" disabled={!active.finalSaved} onClick={() => setTab('backtest')}>Backtesting →</button></div>
      </div>
    </section>}

    {tab === 'backtest' && active && activePlan && <>
      <section className="aw-card">
        <header><span>▣</span><h2>Backtesting &amp; Execution</h2>{stockPicker}</header>
        <div className="aw-panel-body">
          <div className="aw-setup"><b>{active.recommendation.symbol}</b><p>{tradeSummary(active.recommendation, activePlan)}</p><p className={active.finalSaved ? 'positive' : 'aw-muted'}>Status: {active.finalSaved ? 'Final assessment saved' : 'Final assessment pending'}</p></div>
          <div className="aw-buttons"><button type="button" className="aw-primary" disabled={backtestBlocked} onClick={() => void submitBacktest()}>Submit to FYERS Backtesting</button></div>
          {backtestBlocked && <p className="aw-muted small">Backtesting needs a saved final assessment, a valid trade, capital that covers the selected positions and valid risk settings.</p>}
        </div>
      </section>
      <section className="aw-card">
        <header><span>◷</span><h2>Backtest Results</h2></header>
        <div className="aw-panel-body"><p className="aw-muted">Awaiting FYERS backtesting integration. Win rate, drawdown, profit factor, slippage and historical trades will appear here.</p><div className="aw-buttons"><button type="button" className="aw-primary" disabled>Approve Live Order</button></div></div>
      </section>
    </>}

    {notice && <div className="aw-notice" role="status"><span>✓</span>{notice}<button type="button" aria-label="Dismiss notice" onClick={() => setNotice('')}>×</button></div>}
    <p className="aw-disclaimer">{dataSource.live ? 'Live data source connected.' : 'All data is illustrative. No backend, broker API, webhook, persistence or execution is connected yet.'} Manual review does not substitute for AI-generated final reconciliation.</p>

    {editing && editForm && <div className="aw-modal-backdrop" role="presentation" onMouseDown={() => setEditing(undefined)}>
      <form className="aw-modal" role="dialog" aria-modal="true" aria-label={`Edit trade ${editing}`} onSubmit={saveEdit} onMouseDown={(event) => event.stopPropagation()}>
        <header><div><span>EDIT TRADE</span><h2>{editing}</h2></div><button type="button" aria-label="Close" onClick={() => setEditing(undefined)}>×</button></header>
        <div className="aw-modal-grid">{([['entry', 'Entry price'], ['stopLoss', 'Stop loss'], ['target1', 'Target 1'], ['target2', 'Target 2'], ['quantity', 'Quantity'], ['confidence', 'Confidence %']] as const).map(([key, label]) =>
          <label key={key} className="aw-field">{label}<input type="number" step="any" value={editForm[key]} onChange={(event) => setEditForm({ ...editForm, [key]: event.target.value })} /></label>)}
          <label className="aw-field">Action<select value={editForm.action} onChange={(event) => setEditForm({ ...editForm, action: event.target.value as TradeAction })}><option>BUY</option><option>SELL</option></select></label>
        </div>
        <label className="aw-field">Reasoning / user override notes<textarea value={editForm.reasoning} onChange={(event) => setEditForm({ ...editForm, reasoning: event.target.value })} /></label>
        <p className="aw-modal-warning">{editError || 'Changing trade parameters clears the saved FIA and final reviews for this stock.'}</p>
        <footer><button type="button" className="aw-secondary" onClick={() => setEditing(undefined)}>Cancel</button><button type="submit" className="aw-primary">Save changes</button></footer>
      </form>
    </div>}
  </div>;
}

function Stage({ row }: { row: Row }) {
  const [label, tone] = row.finalSaved ? ['Final saved', 'done'] : row.fiaSaved ? ['FIA saved', 'progress'] : row.fiaRequested ? ['FIA requested', 'progress'] : ['Level 2 AI', 'idle'];
  return <span className={`aw-stage ${tone}`}>{label}</span>;
}
