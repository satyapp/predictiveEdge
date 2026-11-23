import { FormEvent, useMemo, useState } from 'react';

type AutomationStatus = 'ACTIVE' | 'PAUSED' | 'DRAFT';

type AutomationRule = {
  id: number;
  name: string;
  strategy: string;
  universe: string;
  schedule: string;
  risk: string;
  status: AutomationStatus;
  lastRun: string;
  signals: number;
};

const initialRules: AutomationRule[] = [
  { id: 1, name: 'Opening Range Breakout', strategy: 'ORB · 15 minute range', universe: 'Intraday List', schedule: '09:30–11:30 IST', risk: '0.75% per trade', status: 'ACTIVE', lastRun: 'Today, 09:31', signals: 4 },
  { id: 2, name: 'Momentum Continuation', strategy: 'Volume + relative strength', universe: 'Top 20 ranked stocks', schedule: '10:00–14:30 IST', risk: '0.50% per trade', status: 'ACTIVE', lastRun: 'Today, 10:02', signals: 2 },
  { id: 3, name: 'VWAP Reclaim', strategy: 'VWAP + delivery volume', universe: 'Custom watchlist', schedule: '11:00–14:45 IST', risk: '0.50% per trade', status: 'PAUSED', lastRun: '21 Nov, 13:16', signals: 0 },
  { id: 4, name: 'Closing Strength Scan', strategy: 'Range expansion', universe: 'NIFTY 100', schedule: '14:30–15:10 IST', risk: '0.40% per trade', status: 'DRAFT', lastRun: 'Not run yet', signals: 0 },
];

const runHistory = [
  { time: '10:02:14', rule: 'Momentum Continuation', outcome: '2 signals', note: 'RELIANCE, SBIN qualified', tone: 'success' },
  { time: '09:31:08', rule: 'Opening Range Breakout', outcome: '4 signals', note: 'All orders routed to paper trading', tone: 'success' },
  { time: '09:15:00', rule: 'Session preflight', outcome: 'Passed', note: 'Broker data, limits and kill switch verified', tone: 'success' },
  { time: '21 Nov · 14:46', rule: 'VWAP Reclaim', outcome: 'No trade', note: 'Daily loss guard prevented a new position', tone: 'guarded' },
];

function Toggle({ checked, label, onChange, disabled = false }: { checked: boolean; label: string; onChange: () => void; disabled?: boolean }) {
  return <button type="button" className={`auto-toggle ${checked ? 'on' : ''}`} aria-label={label} aria-pressed={checked} onClick={onChange} disabled={disabled}><span /></button>;
}

export function AutomationPage() {
  const [rules, setRules] = useState(initialRules);
  const [masterEnabled, setMasterEnabled] = useState(true);
  const [tab, setTab] = useState<'rules' | 'history'>('rules');
  const [filter, setFilter] = useState<'ALL' | AutomationStatus>('ALL');
  const [showCreate, setShowCreate] = useState(false);
  const [notice, setNotice] = useState('');
  const [form, setForm] = useState({ name: '', strategy: 'Opening Range Breakout', universe: 'Intraday List', schedule: '09:30–11:30 IST', risk: '0.50% per trade' });

  const visibleRules = useMemo(() => rules.filter((rule) => filter === 'ALL' || rule.status === filter), [filter, rules]);
  const activeCount = masterEnabled ? rules.filter((rule) => rule.status === 'ACTIVE').length : 0;

  function changeStatus(id: number) {
    setRules((current) => current.map((rule) => rule.id === id ? { ...rule, status: rule.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE' } : rule));
    setNotice('Automation status updated. Live execution remains locked.');
  }

  function pauseAll() {
    setMasterEnabled(false);
    setRules((current) => current.map((rule) => rule.status === 'ACTIVE' ? { ...rule, status: 'PAUSED' } : rule));
    setNotice('Emergency pause applied. All automated runs are stopped.');
  }

  function runNow(rule: AutomationRule) {
    setRules((current) => current.map((item) => item.id === rule.id ? { ...item, lastRun: 'Just now' } : item));
    setNotice(`${rule.name} completed a paper-trading scan. No live order was placed.`);
  }

  function createRule(event: FormEvent) {
    event.preventDefault();
    setRules((current) => [...current, { id: Date.now(), ...form, status: 'DRAFT', lastRun: 'Not run yet', signals: 0 }]);
    setShowCreate(false);
    setForm((current) => ({ ...current, name: '' }));
    setTab('rules');
    setFilter('ALL');
    setNotice('Draft automation created. Review its guardrails before activation.');
  }

  return <div className="automation-page">
    <section className="auto-hero">
      <div><p className="eyebrow">RULE-BASED EXECUTION</p><h1>Automation Control Center</h1><p>Schedule repeatable market scans, enforce risk rules, and supervise every paper-trading run from one place.</p></div>
      <div className="auto-hero-actions"><span className="auto-mode"><i /> PAPER MODE</span><button type="button" className="auto-create" onClick={() => setShowCreate(true)}>＋ Create automation</button></div>
    </section>

    <section className="auto-metrics" aria-label="Automation summary">
      <article><span className="metric-icon violet">▷</span><div><small>ACTIVE AUTOMATIONS</small><strong>{activeCount}</strong><p>{masterEnabled ? 'Ready for scheduled runs' : 'Master switch is paused'}</p></div></article>
      <article><span className="metric-icon blue">◷</span><div><small>TODAY'S RUNS</small><strong>12</strong><p>11 passed · 1 guarded</p></div></article>
      <article><span className="metric-icon amber">◇</span><div><small>SIGNALS GENERATED</small><strong>6</strong><p>Paper orders only</p></div></article>
      <article><span className="metric-icon green">✓</span><div><small>RISK HEALTH</small><strong>Healthy</strong><p>All safety checks online</p></div></article>
    </section>

    <section className={`auto-master ${masterEnabled ? '' : 'paused'}`}>
      <div className="auto-master-copy"><span className="master-symbol">ϟ</span><div><p className="eyebrow">GLOBAL CONTROL</p><h2>Automation master switch</h2><p>{masterEnabled ? 'Scheduled paper-trading runs can start when their individual rules are active.' : 'All scheduled starts are blocked until the master switch is enabled.'}</p></div></div>
      <div className="master-actions"><label><span><b>{masterEnabled ? 'Automation enabled' : 'Automation paused'}</b><small>Applies to every strategy</small></span><Toggle checked={masterEnabled} label="Toggle all automation" onChange={() => setMasterEnabled((value) => !value)} /></label><button type="button" className="emergency" onClick={pauseAll}>■ Emergency pause</button></div>
    </section>

    <div className="auto-layout">
      <section className="auto-main-panel">
        <header className="auto-tabs"><div role="tablist" aria-label="Automation data"><button type="button" role="tab" aria-selected={tab === 'rules'} className={tab === 'rules' ? 'active' : ''} onClick={() => setTab('rules')}>Automation rules <span>{rules.length}</span></button><button type="button" role="tab" aria-selected={tab === 'history'} className={tab === 'history' ? 'active' : ''} onClick={() => setTab('history')}>Run history</button></div>{tab === 'rules' && <select aria-label="Filter automations" value={filter} onChange={(event) => setFilter(event.target.value as typeof filter)}><option value="ALL">All statuses</option><option value="ACTIVE">Active</option><option value="PAUSED">Paused</option><option value="DRAFT">Drafts</option></select>}</header>

        {tab === 'rules' ? <div className="auto-rule-list">{visibleRules.map((rule) => <article className="auto-rule" key={rule.id}>
          <div className="rule-top"><span className="rule-icon">◎</span><div><div className="rule-name"><h3>{rule.name}</h3><span className={`status ${rule.status.toLowerCase()}`}>{rule.status}</span></div><p>{rule.strategy}</p></div><Toggle checked={rule.status === 'ACTIVE'} label={`${rule.status === 'ACTIVE' ? 'Pause' : 'Activate'} ${rule.name}`} disabled={!masterEnabled && rule.status !== 'ACTIVE'} onChange={() => changeStatus(rule.id)} /></div>
          <dl><div><dt>UNIVERSE</dt><dd>{rule.universe}</dd></div><div><dt>SCHEDULE</dt><dd>{rule.schedule}</dd></div><div><dt>MAXIMUM RISK</dt><dd>{rule.risk}</dd></div><div><dt>LAST RUN</dt><dd>{rule.lastRun}</dd></div></dl>
          <footer><span><i /> {rule.signals} signals today</span><div><button type="button" className="text-button">Edit rule</button><button type="button" className="run-button" onClick={() => runNow(rule)} disabled={!masterEnabled || rule.status !== 'ACTIVE'}>Run paper scan</button></div></footer>
        </article>)}{visibleRules.length === 0 && <div className="auto-empty"><span>◎</span><h3>No matching automations</h3><p>Choose another status or create a new rule.</p></div>}</div> : <div className="run-history"><div className="history-head"><span>TIME</span><span>WORKFLOW</span><span>OUTCOME</span><span>DETAIL</span></div>{runHistory.map((run) => <div className="history-row" key={`${run.time}-${run.rule}`}><time>{run.time}</time><b>{run.rule}</b><span className={run.tone}>{run.outcome}</span><p>{run.note}</p></div>)}</div>}
      </section>

      <aside className="auto-side">
        <section><header><span>⌾</span><div><h2>Session guardrails</h2><p>Applied before every run</p></div><em>4/4 ONLINE</em></header><ul className="guardrails"><li><span>✓</span><div><b>Daily loss limit</b><small>Stop at −1.50% or ₹7,500</small></div></li><li><span>✓</span><div><b>Position exposure</b><small>Maximum 3 concurrent positions</small></div></li><li><span>✓</span><div><b>Order protection</b><small>Stop-loss required on every entry</small></div></li><li><span>✓</span><div><b>Market hours lock</b><small>No entries after 15:10 IST</small></div></li></ul><button type="button" className="side-link">Review risk settings</button></section>
        <section className="next-run"><header><span>◷</span><div><h2>Next scheduled run</h2><p>Opening Range Breakout</p></div></header><strong>Tomorrow · 09:30 IST</strong><div><span>Intraday List</span><span>Paper trading</span></div><p>Preflight begins at 09:15 IST. The rule will not run if a safety check fails.</p></section>
        <section className="safety-note"><span>⌁</span><div><b>Live execution is locked</b><p>Automations can analyze signals and simulate orders. Broker execution requires separate approval and quality gates.</p></div></section>
      </aside>
    </div>

    {notice && <div className="auto-notice" role="status"><span>✓</span>{notice}<button type="button" aria-label="Dismiss notice" onClick={() => setNotice('')}>×</button></div>}

    {showCreate && <div className="auto-modal-backdrop" role="presentation" onMouseDown={() => setShowCreate(false)}><form className="auto-modal" onSubmit={createRule} onMouseDown={(event) => event.stopPropagation()}><header><div><p className="eyebrow">NEW WORKFLOW</p><h2>Create automation</h2><p>New rules begin as drafts and cannot place live orders.</p></div><button type="button" aria-label="Close" onClick={() => setShowCreate(false)}>×</button></header><label>Automation name<input autoFocus value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="e.g. Morning breakout scan" required /></label><div className="modal-grid"><label>Strategy<select value={form.strategy} onChange={(event) => setForm({ ...form, strategy: event.target.value })}><option>Opening Range Breakout</option><option>Momentum Continuation</option><option>VWAP Reclaim</option><option>Range Expansion</option></select></label><label>Stock universe<select value={form.universe} onChange={(event) => setForm({ ...form, universe: event.target.value })}><option>Intraday List</option><option>Top 20 ranked stocks</option><option>Custom watchlist</option><option>NIFTY 100</option></select></label><label>Schedule<input value={form.schedule} onChange={(event) => setForm({ ...form, schedule: event.target.value })} required /></label><label>Maximum risk<select value={form.risk} onChange={(event) => setForm({ ...form, risk: event.target.value })}><option>0.25% per trade</option><option>0.50% per trade</option><option>0.75% per trade</option><option>1.00% per trade</option></select></label></div><div className="draft-warning"><span>◇</span><p><b>Saved as draft</b><br />Review conditions and risk controls before activating this automation.</p></div><footer><button type="button" className="cancel" onClick={() => setShowCreate(false)}>Cancel</button><button type="submit" className="auto-create">Create draft</button></footer></form></div>}
  </div>;
}
