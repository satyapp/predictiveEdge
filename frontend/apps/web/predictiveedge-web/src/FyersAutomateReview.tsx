import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { AuthSession } from './auth';
import {
  AutomateWebhook, buildFinal, createReview, deleteWebhook, deliverReview, latest, LearningSummary, loadLearning,
  loadReviews, loadWebhooks, recordFiaReview, recordGuardianRevision, recordOutcome, RecommendationDraft,
  RecommendationReview, RecommendationStage, RecommendationVersion, registerWebhook, setWebhookEnabled, stageLabels,
  statusLabels, testWebhook, TradeAction,
} from './fyersAutomate';
import './fyers-automate.css';

const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const when = new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
const fmt = (value?: number | null) => value == null ? '—' : money.format(Number(value));
const time = (value?: string) => value ? when.format(new Date(value)) : '—';
const message = (failure: unknown, fallback: string) => failure instanceof Error ? failure.message : fallback;

// ---- Draft form ------------------------------------------------------------------------------------------------

type DraftFields = Record<'entryLow' | 'entryHigh' | 'stopLoss' | 'targetOne' | 'targetTwo' | 'quantity' | 'confidence', string>;
const emptyFields: DraftFields = { entryLow: '', entryHigh: '', stopLoss: '', targetOne: '', targetTwo: '', quantity: '', confidence: '' };

function fieldsFrom(version?: RecommendationDraft): DraftFields {
  if (!version) return emptyFields;
  const text = (value?: number | null) => value == null ? '' : String(value);
  return { entryLow: text(version.entryLow), entryHigh: text(version.entryHigh), stopLoss: text(version.stopLoss),
    targetOne: text(version.targetOne), targetTwo: text(version.targetTwo), quantity: text(version.quantity), confidence: text(version.confidence) };
}

function DraftForm({ title, hint, rationaleLabel, initial, submitLabel, busy, onSubmit }: {
  title: string; hint: string; rationaleLabel: string; initial?: RecommendationDraft; submitLabel: string; busy: boolean;
  onSubmit: (draft: RecommendationDraft) => Promise<void>;
}) {
  const [action, setAction] = useState<TradeAction>(initial?.action ?? 'BUY');
  const [fields, setFields] = useState<DraftFields>(() => fieldsFrom(initial));
  const [rationale, setRationale] = useState('');
  const directional = action === 'BUY' || action === 'SELL';
  const update = (name: keyof DraftFields, value: string) => setFields((current) => ({ ...current, [name]: value }));
  const num = (value: string) => value.trim() === '' ? null : Number(value);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await onSubmit({ action, rationale, confidence: num(fields.confidence),
      ...(directional ? { entryLow: num(fields.entryLow), entryHigh: num(fields.entryHigh), stopLoss: num(fields.stopLoss),
        targetOne: num(fields.targetOne), targetTwo: num(fields.targetTwo), quantity: num(fields.quantity) } : {}) });
    setRationale('');
  }

  const input = (name: keyof DraftFields, label: string, step = '0.05') =>
    <label>{label}<input type="number" min="0" step={step} inputMode="decimal" value={fields[name]} onChange={(event) => update(name, event.target.value)} /></label>;

  return <form className="draft-form" onSubmit={submit}>
    <div><b>{title}</b><p>{hint}</p></div>
    <div className="draft-grid">
      <label>Action<select value={action} onChange={(event) => setAction(event.target.value as TradeAction)}>
        <option value="BUY">BUY</option><option value="SELL">SELL</option><option value="WAIT">WAIT</option><option value="NO_TRADE">NO TRADE</option>
      </select></label>
      {directional && <>
        {input('entryLow', 'Entry from')}{input('entryHigh', 'Entry to')}{input('stopLoss', 'Stop loss')}
        {input('targetOne', 'Target 1')}{input('targetTwo', 'Target 2')}{input('quantity', 'Quantity', '1')}
      </>}
      {input('confidence', 'Confidence %', '1')}
    </div>
    <label className="wide">{rationaleLabel}<textarea rows={4} value={rationale} onChange={(event) => setRationale(event.target.value)} maxLength={8000} /></label>
    <button className="automate-primary" disabled={busy}>{busy ? 'Working…' : submitLabel}</button>
  </form>;
}

// ---- Version cards ---------------------------------------------------------------------------------------------

function VersionCard({ stage, version, empty }: { stage: RecommendationStage; version?: RecommendationVersion; empty: string }) {
  const tone = version ? version.action.toLowerCase().replace('_', '-') : 'none';
  return <article className={`version-card ${tone}`}>
    <header><small>{stageLabels[stage]}</small>{version && <span className={`action-pill ${tone}`}>{version.action.replace('_', ' ')}</span>}</header>
    {!version ? <p className="version-empty">{empty}</p> : <>
      <dl>
        <div><dt>Entry</dt><dd>{version.entryLow == null ? '—' : version.entryLow === version.entryHigh ? fmt(version.entryLow) : `${fmt(version.entryLow)} – ${fmt(version.entryHigh)}`}</dd></div>
        <div><dt>Stop</dt><dd>{fmt(version.stopLoss)}</dd></div>
        <div><dt>Target 1</dt><dd>{fmt(version.targetOne)}</dd></div>
        <div><dt>Target 2</dt><dd>{fmt(version.targetTwo)}</dd></div>
        <div><dt>Quantity</dt><dd>{version.quantity ?? '—'}</dd></div>
        <div><dt>Confidence</dt><dd>{version.confidence == null ? '—' : `${version.confidence}%`}</dd></div>
      </dl>
      {version.rationale && <p className="version-why">{version.rationale}</p>}
      <footer>{version.source} · {time(version.createdAt)}</footer>
    </>}
  </article>;
}

export function ReviewComparison({ review }: { review: RecommendationReview }) {
  const guardian = latest(review, 'GUARDIAN_REVISION');
  return <div className="version-row">
    <VersionCard stage="PREDICTIVEEDGE_STAGE_ONE" version={latest(review, 'PREDICTIVEEDGE_STAGE_ONE')} empty="No stage-one recommendation." />
    <VersionCard stage="FIA_REVIEW" version={latest(review, 'FIA_REVIEW')} empty="Review it with FIA in FYERS Automate, then record FIA's correction below." />
    <VersionCard stage="FINAL_AI_FIA" version={latest(review, 'FINAL_AI_FIA')} empty="Built by the PredictiveEdge AI engine once FIA's review is recorded." />
    {guardian && <VersionCard stage="GUARDIAN_REVISION" version={guardian} empty="" />}
  </div>;
}

// ---- Webhooks --------------------------------------------------------------------------------------------------

function WebhookPanel({ token, webhooks, onChanged, setNotice }: {
  token: string; webhooks: AutomateWebhook[]; onChanged: () => Promise<void>; setNotice: (value: string) => void;
}) {
  const [label, setLabel] = useState('');
  const [url, setUrl] = useState('');
  const [busy, setBusy] = useState(false);

  async function run(work: () => Promise<string>) {
    setBusy(true);
    try { setNotice(await work()); await onChanged(); }
    catch (failure) { setNotice(message(failure, 'The webhook request failed.')); }
    finally { setBusy(false); }
  }

  return <section className="automate-panel">
    <div className="section-heading"><div><p className="eyebrow">STEP 1 · CONNECT</p><h3>FYERS Automate webhooks</h3>
      <p>In FYERS Automate, add a Webhook trigger to your FIA strategy and paste its URL here. Keep any Place order block out of that strategy: PredictiveEdge only sends recommendations for review.</p></div><span>REVIEW ONLY</span></div>
    <form className="webhook-form" onSubmit={(event) => { event.preventDefault(); void run(async () => {
      await registerWebhook(token, label, url); setLabel(''); setUrl(''); return 'Webhook saved. Send a test to confirm FYERS accepts it.'; }); }}>
      <label>Label<input value={label} onChange={(event) => setLabel(event.target.value)} placeholder="FIA review strategy" maxLength={120} /></label>
      <label className="grow">Webhook URL<input type="url" required value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://…fyers.in/…" autoComplete="off" /></label>
      <button className="automate-primary" disabled={busy || !url}>Add webhook</button>
    </form>
    {webhooks.length === 0 ? <p className="automate-empty">No webhooks yet. Recommendations can still be saved here and sent later.</p>
      : <ul className="webhook-list">{webhooks.map((webhook) => <li key={webhook.webhookId} className={webhook.enabled ? '' : 'off'}>
        <div><b>{webhook.label}</b><code>{webhook.maskedUrl}</code><small>Last call {time(webhook.lastDeliveredAt)}</small></div>
        <button type="button" disabled={busy} onClick={() => void run(async () => {
          const result = await testWebhook(token, webhook.webhookId);
          return result.succeeded ? `Test delivered (HTTP ${result.httpStatus}).` : `Test failed: ${result.error}`; })}>Send test</button>
        <button type="button" disabled={busy} onClick={() => void run(async () => {
          await setWebhookEnabled(token, webhook.webhookId, !webhook.enabled); return webhook.enabled ? 'Webhook turned off.' : 'Webhook turned on.'; })}>{webhook.enabled ? 'Turn off' : 'Turn on'}</button>
        <button type="button" className="danger" disabled={busy} onClick={() => void run(async () => {
          await deleteWebhook(token, webhook.webhookId); return 'Webhook removed.'; })}>Remove</button>
      </li>)}</ul>}
  </section>;
}

// ---- Review detail ---------------------------------------------------------------------------------------------

function ReviewDetail({ token, review, webhooks, onUpdated, setNotice }: {
  token: string; review: RecommendationReview; webhooks: AutomateWebhook[];
  onUpdated: (review: RecommendationReview) => void; setNotice: (value: string) => void;
}) {
  const enabled = webhooks.filter((value) => value.enabled);
  const [webhookId, setWebhookId] = useState(enabled[0]?.webhookId ?? '');
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState({ quantity: '', entryPrice: '', exitPrice: '', enteredAt: '', exitedAt: '', charges: '', notes: '' });
  const stageOne = latest(review, 'PREDICTIVEEDGE_STAGE_ONE');
  const fia = latest(review, 'FIA_REVIEW');
  const final = latest(review, 'FINAL_AI_FIA');
  const guardian = latest(review, 'GUARDIAN_REVISION');
  const current = guardian ?? final ?? fia ?? stageOne;
  const closed = review.status === 'CLOSED';
  useEffect(() => { if (!webhooks.some((w) => w.webhookId === webhookId && w.enabled)) setWebhookId(enabled[0]?.webhookId ?? ''); }, [webhooks]); // eslint-disable-line react-hooks/exhaustive-deps

  async function run(work: () => Promise<string>) {
    setBusy(true);
    try { setNotice(await work()); }
    catch (failure) { setNotice(message(failure, 'The request failed.')); }
    finally { setBusy(false); }
  }

  const send = (stage: RecommendationStage) => run(async () => {
    const delivery = await deliverReview(token, review.reviewId, stage, webhookId);
    const refreshed = await loadReviews(token, review.symbol);
    const updated = refreshed.find((value) => value.reviewId === review.reviewId);
    if (updated) onUpdated(updated);
    return delivery.succeeded ? `${stageLabels[stage]} sent to FYERS Automate (HTTP ${delivery.httpStatus}). No order was placed.`
      : `Delivery failed: ${delivery.error}`;
  });

  return <section className="review-detail">
    <div className="review-head">
      <div><p className="eyebrow">{review.fyersSymbol} · {review.sourceRef}</p><h3>{review.symbol}</h3><span className="status-pill">{statusLabels[review.status]}</span></div>
      {!closed && <label>Send to<select value={webhookId} onChange={(event) => setWebhookId(event.target.value)}>
        {enabled.length === 0 && <option value="">No enabled webhook</option>}
        {enabled.map((webhook) => <option key={webhook.webhookId} value={webhook.webhookId}>{webhook.label}</option>)}
      </select></label>}
    </div>

    <ReviewComparison review={review} />

    {!closed && <div className="loop-steps">
      <div className={`loop-step ${review.deliveries.some((d) => d.stage === 'PREDICTIVEEDGE_STAGE_ONE' && d.succeeded) ? 'done' : ''}`}>
        <b>1 · Send stage 1 to FYERS</b><p>Triggers your Automate strategy so you can review it with FIA.</p>
        <button type="button" disabled={busy || !webhookId} onClick={() => void send('PREDICTIVEEDGE_STAGE_ONE')}>Send stage 1</button>
      </div>
      <div className={`loop-step ${fia ? 'done' : ''}`}>
        <DraftForm key={`fia-${fia?.versionId ?? 'new'}`} title="2 · Record FIA's review" hint="Open the strategy in FYERS Automate, ask FIA to review and correct it, then copy FIA's levels and reasoning here."
          rationaleLabel="FIA's reasoning and corrections (paste)" initial={fia ?? stageOne} submitLabel={fia ? 'Update FIA review' : 'Save FIA review'} busy={busy}
          onSubmit={(draft) => run(async () => { onUpdated(await recordFiaReview(token, review.reviewId, draft)); return 'FIA review saved. Build the final recommendation next.'; })} />
      </div>
      <div className={`loop-step ${final ? 'done' : ''}`}>
        <b>3 · Build final (AI + FIA)</b><p>The PredictiveEdge AI engine weighs both recommendations and your realized track record.</p>
        <button type="button" disabled={busy || !fia} onClick={() => void run(async () => { onUpdated(await buildFinal(token, review.reviewId)); return 'Final recommendation built.'; })}>{final ? 'Rebuild final' : 'Build final'}</button>
      </div>
      <div className={`loop-step ${review.status === 'FINAL_SENT' ? 'done' : ''}`}>
        <b>4 · Send final to FYERS</b><p>Review only. You confirm any live order yourself in FYERS.</p>
        <button type="button" disabled={busy || !final || !webhookId} onClick={() => void send('FINAL_AI_FIA')}>Send final</button>
      </div>
    </div>}

    {!closed && final && <details className="guardian-box">
      <summary><b>Trade Guardian</b><span>Placeholder · advisory only</span></summary>
      <p>Trade Guardian will monitor the open position and propose revisions when market conditions change. Until its monitoring logic ships, you can record a revision by hand. It never changes an order in FYERS; send it to Automate to review and act on it yourself.</p>
      <DraftForm key={`g-${guardian?.versionId ?? 'new'}`} title="Record a Guardian revision" hint="For example, trail the stop after a strong first hour." rationaleLabel="Why the plan changed"
        initial={guardian ?? final} submitLabel="Save revision" busy={busy}
        onSubmit={(draft) => run(async () => { onUpdated(await recordGuardianRevision(token, review.reviewId, draft)); return 'Guardian revision saved.'; })} />
      {guardian && <button type="button" disabled={busy || !webhookId} onClick={() => void send('GUARDIAN_REVISION')}>Send revision to FYERS</button>}
    </details>}

    {!closed && current && (current.action === 'BUY' || current.action === 'SELL') && <form className="outcome-form" onSubmit={(event) => {
      event.preventDefault();
      void run(async () => {
        onUpdated(await recordOutcome(token, review.reviewId, { direction: current.action as 'BUY' | 'SELL',
          quantity: Number(outcome.quantity), entryPrice: Number(outcome.entryPrice), exitPrice: Number(outcome.exitPrice),
          enteredAt: new Date(outcome.enteredAt).toISOString(), exitedAt: new Date(outcome.exitedAt).toISOString(),
          charges: outcome.charges ? Number(outcome.charges) : 0, followedStage: current.stage, notes: outcome.notes }));
        return 'Trade closed. Its P/L now feeds future recommendations.';
      });
    }}>
      <div><b>5 · Close the trade</b><p>After exiting in FYERS, record what actually happened. P/L is calculated for you and used for learning.</p></div>
      <div className="draft-grid">
        <label>Quantity<input type="number" min="1" step="1" required value={outcome.quantity} onChange={(e) => setOutcome({ ...outcome, quantity: e.target.value })} /></label>
        <label>Entry price<input type="number" min="0" step="0.05" required value={outcome.entryPrice} onChange={(e) => setOutcome({ ...outcome, entryPrice: e.target.value })} /></label>
        <label>Exit price<input type="number" min="0" step="0.05" required value={outcome.exitPrice} onChange={(e) => setOutcome({ ...outcome, exitPrice: e.target.value })} /></label>
        <label>Charges ₹<input type="number" min="0" step="0.01" value={outcome.charges} onChange={(e) => setOutcome({ ...outcome, charges: e.target.value })} /></label>
        <label>Entered at<input type="datetime-local" required value={outcome.enteredAt} onChange={(e) => setOutcome({ ...outcome, enteredAt: e.target.value })} /></label>
        <label>Exited at<input type="datetime-local" required value={outcome.exitedAt} onChange={(e) => setOutcome({ ...outcome, exitedAt: e.target.value })} /></label>
      </div>
      <label className="wide">Notes<input value={outcome.notes} onChange={(e) => setOutcome({ ...outcome, notes: e.target.value })} /></label>
      <button className="automate-primary" disabled={busy}>Record outcome</button>
    </form>}

    {review.outcome && <div className={`outcome-banner ${review.outcome.realizedPnl >= 0 ? 'win' : 'loss'}`}>
      <b>{review.outcome.realizedPnl >= 0 ? 'Profit' : 'Loss'} {fmt(review.outcome.realizedPnl)}</b>
      <span>{review.outcome.direction} {review.outcome.quantity} @ {fmt(review.outcome.entryPrice)} → {fmt(review.outcome.exitPrice)} · {review.outcome.returnPercent}% · followed {stageLabels[review.outcome.followedStage]}</span>
    </div>}

    {review.deliveries.length > 0 && <details className="delivery-log"><summary>Webhook deliveries ({review.deliveries.length})</summary><ul>
      {review.deliveries.map((d) => <li key={d.deliveryId} className={d.succeeded ? 'ok' : 'bad'}>{time(d.sentAt)} · {d.stage ? stageLabels[d.stage] : 'Test'} · {d.succeeded ? `HTTP ${d.httpStatus}` : d.error}</li>)}
    </ul></details>}
  </section>;
}

// ---- Page ------------------------------------------------------------------------------------------------------

export function FyersAutomateReview({ session, focusReviewId }: { session: AuthSession; focusReviewId?: string }) {
  const token = session.accessToken;
  const [webhooks, setWebhooks] = useState<AutomateWebhook[]>([]);
  const [reviews, setReviews] = useState<RecommendationReview[]>([]);
  const [selectedId, setSelectedId] = useState<string | undefined>(focusReviewId);
  const [symbol, setSymbol] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    const [hooks, list] = await Promise.all([loadWebhooks(token), loadReviews(token)]);
    setWebhooks(hooks); setReviews(list);
  }, [token]);

  useEffect(() => { void refresh().catch((failure) => setNotice(message(failure, 'FYERS Automate review is unavailable.'))); }, [refresh]);
  useEffect(() => { if (focusReviewId) setSelectedId(focusReviewId); }, [focusReviewId]);

  const open = reviews.filter((review) => review.status !== 'CLOSED');
  const selected = reviews.find((review) => review.reviewId === selectedId) ?? open[0];
  const replace = (updated: RecommendationReview) =>
    setReviews((current) => current.map((value) => value.reviewId === updated.reviewId ? updated : value));

  return <section className="automate-page">
    <div className="intraday-title"><div><p className="eyebrow">FYERS AUTOMATE · FIA REVIEW LOOP</p><h2>FYERS Automate review</h2>
      <p>Send a PredictiveEdge recommendation to your FYERS Automate strategy, review and correct it with FIA, record FIA's answer here, and let the AI engine build the final version to send back.</p></div>
      <span>NO LIVE ORDERS FROM PREDICTIVEEDGE</span></div>
    {notice && <p className="engine-note" role="status">{notice}</p>}

    <WebhookPanel token={token} webhooks={webhooks} onChanged={refresh} setNotice={setNotice} />

    <section className="automate-panel">
      <div className="section-heading"><div><p className="eyebrow">STAGE 1 · NEW RECOMMENDATION</p><h3>Add a recommendation by hand</h3>
        <p>Recommendations from the Intraday Screener arrive here automatically when you press Send to FYERS Automate on a stock.</p></div></div>
      <div className="manual-symbol"><label>NSE symbol<input value={symbol} onChange={(event) => setSymbol(event.target.value.toUpperCase())} placeholder="SBIN" maxLength={40} /></label></div>
      <DraftForm title="PredictiveEdge stage-1 recommendation" hint="Saved as stage 1 and sent to your first enabled webhook." rationaleLabel="Reasoning"
        submitLabel="Save and send to FYERS" busy={busy} onSubmit={async (draft) => {
          if (!symbol.trim()) { setNotice('Enter the NSE symbol first.'); return; }
          setBusy(true);
          try {
            const webhook = webhooks.find((value) => value.enabled);
            const review = await createReview(token, { exchange: 'NSE', symbol, sourceRef: 'manual', source: 'PredictiveEdge (entered by trader)',
              webhookId: webhook?.webhookId, recommendation: draft });
            const delivery = review.deliveries.at(-1);
            setNotice(!webhook ? 'Saved. Add a webhook to send it to FYERS.' : delivery?.succeeded
              ? `Sent ${review.symbol} to FYERS Automate. Review it with FIA, then record FIA's answer.` : `Saved, but delivery failed: ${delivery?.error}`);
            await refresh(); setSelectedId(review.reviewId); setSymbol('');
          } catch (failure) { setNotice(message(failure, 'The recommendation could not be saved.')); }
          finally { setBusy(false); }
        }} />
    </section>

    <section className="automate-panel">
      <div className="section-heading"><div><p className="eyebrow">STEPS 2–5 · REVIEW QUEUE</p><h3>Open recommendations</h3>
        <p>Closed trades move to Trade history.</p></div><span>{open.length} OPEN</span></div>
      {open.length === 0 ? <p className="automate-empty">No open recommendations.</p> : <div className="review-tabs">
        {open.map((review) => { const current = latest(review, 'GUARDIAN_REVISION') ?? latest(review, 'FINAL_AI_FIA') ?? latest(review, 'FIA_REVIEW') ?? latest(review, 'PREDICTIVEEDGE_STAGE_ONE');
          return <button type="button" key={review.reviewId} className={selected?.reviewId === review.reviewId ? 'active' : ''} onClick={() => setSelectedId(review.reviewId)}>
            <b>{review.symbol}</b><span className={`action-pill ${current?.action.toLowerCase().replace('_', '-') ?? ''}`}>{current?.action.replace('_', ' ')}</span><small>{statusLabels[review.status]}</small>
          </button>; })}
      </div>}
      {selected && selected.status !== 'CLOSED' && <ReviewDetail key={selected.reviewId} token={token} review={selected} webhooks={webhooks} onUpdated={replace} setNotice={setNotice} />}
    </section>
  </section>;
}

// ---- Trade history ---------------------------------------------------------------------------------------------

export function TradeHistory({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [reviews, setReviews] = useState<RecommendationReview[]>([]);
  const [learning, setLearning] = useState<LearningSummary>();
  const [filter, setFilter] = useState('');
  const [expanded, setExpanded] = useState<string>();
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([loadReviews(token), loadLearning(token)])
      .then(([list, summary]) => { setReviews(list); setLearning(summary); })
      .catch((failure) => setError(message(failure, 'Trade history is unavailable.')));
  }, [token]);

  const shown = useMemo(() => reviews.filter((review) => !filter || review.symbol.includes(filter.trim().toUpperCase())), [reviews, filter]);
  const action = (review: RecommendationReview, stage: RecommendationStage) => latest(review, stage)?.action.replace('_', ' ') ?? '—';

  return <section className="automate-page">
    <div className="intraday-title"><div><p className="eyebrow">EVERY STAGE · EVERY STOCK</p><h2>Trade history</h2>
      <p>Each recommendation keeps its PredictiveEdge stage 1, FIA review, final AI + FIA version and any Trade Guardian revision, with the realized P/L once closed.</p></div></div>
    {error && <p className="engine-note" role="alert">{error}</p>}

    {learning && <div className="learning-tiles">
      <div><small>CLOSED TRADES</small><b>{learning.closedTrades}</b></div>
      <div><small>WIN RATE</small><b>{learning.winRatePercent}%</b></div>
      <div><small>TOTAL P/L</small><b className={learning.totalPnl >= 0 ? 'pos' : 'neg'}>{fmt(learning.totalPnl)}</b></div>
      <div><small>PREDICTIVEEDGE DIRECTION RIGHT</small><b>{learning.stageOneDirectionCorrect} / {learning.closedTrades}</b></div>
      <div><small>FIA DIRECTION RIGHT</small><b>{learning.fiaDirectionCorrect} / {learning.closedTrades}</b></div>
      <div><small>FIA AGREED WITH PE</small><b>{learning.fiaAgreedWithStageOne} / {learning.fiaAgreedWithStageOne + learning.fiaDisagreedWithStageOne}</b></div>
    </div>}

    <section className="automate-panel">
      <div className="history-toolbar"><label>Filter by stock<input value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="SBIN" /></label><span>{shown.length} recommendations</span></div>
      {shown.length === 0 ? <p className="automate-empty">No recommendations yet.</p> : <div className="history-table" role="table">
        <div className="history-row head" role="row"><span>Date</span><span>Stock</span><span>Stage 1</span><span>FIA</span><span>Final</span><span>Status</span><span>P/L</span></div>
        {shown.map((review) => <div key={review.reviewId} className="history-entry">
          <button type="button" className="history-row" role="row" onClick={() => setExpanded(expanded === review.reviewId ? undefined : review.reviewId)} aria-expanded={expanded === review.reviewId}>
            <span>{time(review.createdAt)}</span><span><b>{review.symbol}</b></span><span>{action(review, 'PREDICTIVEEDGE_STAGE_ONE')}</span>
            <span>{action(review, 'FIA_REVIEW')}</span><span>{action(review, 'FINAL_AI_FIA')}</span><span>{statusLabels[review.status]}</span>
            <span className={review.outcome ? review.outcome.realizedPnl >= 0 ? 'pos' : 'neg' : ''}>{review.outcome ? fmt(review.outcome.realizedPnl) : '—'}</span>
          </button>
          {expanded === review.reviewId && <div className="history-detail"><ReviewComparison review={review} />
            {review.outcome && <p className="history-outcome">{review.outcome.direction} {review.outcome.quantity} @ {fmt(review.outcome.entryPrice)} → {fmt(review.outcome.exitPrice)}, charges {fmt(review.outcome.charges)}, followed {stageLabels[review.outcome.followedStage]}. {review.outcome.notes}</p>}
          </div>}
        </div>)}
      </div>}
    </section>
  </section>;
}
