import { FormEvent, useEffect, useState } from 'react';
import { AuthSession, clearStoredSession, loadStoredSession, login, logout, register, Registration, resendVerificationOtp, storeSession, toIndianE164, verifyOtp } from './auth';
import { beginZerodhaConnection, BrokerOverview, disconnectZerodhaConnection, loadBrokerOverview, releaseZerodhaBrowserLease } from './broker';
import './broker.css';

type Screen = 'login' | 'register' | 'verify';

function Brand() {
  return <div className="brand"><span className="brand-mark">PE</span><span>Predictive<b>Edge</b></span></div>;
}

function LoginForm({ onAuthenticated, onRegister }: { onAuthenticated: (session: AuthSession) => void; onRegister: () => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try { onAuthenticated(await login(email, password)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Unable to sign in.'); }
    finally { setBusy(false); }
  }

  return <form onSubmit={submit} className="auth-form">
    <p className="eyebrow">SECURE ACCESS</p><h1>Welcome back</h1><p className="lede">Sign in after both your email and mobile number are verified.</p>
    <label>Email address<input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
    <label>Password<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
    {error && <p className="error" role="alert">{error}</p>}
    <button className="primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
    <p className="switch">New to PredictiveEdge? <button type="button" onClick={onRegister}>Create an account</button></p>
  </form>;
}

function RegisterForm({ onAccepted, onLogin }: { onAccepted: (registration: Registration) => void; onLogin: () => void }) {
  const [form, setForm] = useState({ displayName: '', email: '', mobileNumber: '', password: '', confirmPassword: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const update = (field: keyof typeof form, value: string) => setForm((current) => ({ ...current, [field]: value }));

  async function submit(event: FormEvent) {
    event.preventDefault(); setError('');
    if (form.password.length < 12) return setError('Use at least 12 characters for your password.');
    if (form.password !== form.confirmPassword) return setError('Passwords do not match.');
    setBusy(true);
    try {
      onAccepted(await register({ ...form, mobileNumber: toIndianE164(form.mobileNumber) }));
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Registration could not be completed.'); }
    finally { setBusy(false); }
  }

  return <form onSubmit={submit} className="auth-form register-form">
    <p className="eyebrow">CREATE YOUR IDENTITY</p><h1>Start with verified details</h1><p className="lede">We verify email through Amazon SES and mobile through our development SMS adapter.</p>
    <div className="field-grid">
      <label>Full name<input autoComplete="name" value={form.displayName} onChange={(event) => update('displayName', event.target.value)} required maxLength={120} /></label>
      <label>Email address<input type="email" autoComplete="email" value={form.email} onChange={(event) => update('email', event.target.value)} required /></label>
      <label>Mobile number<input inputMode="tel" autoComplete="tel" placeholder="9876543210" value={form.mobileNumber} onChange={(event) => update('mobileNumber', event.target.value)} required /></label>
      <label>Password<input type="password" autoComplete="new-password" value={form.password} onChange={(event) => update('password', event.target.value)} required minLength={12} /></label>
      <label>Confirm password<input type="password" autoComplete="new-password" value={form.confirmPassword} onChange={(event) => update('confirmPassword', event.target.value)} required /></label>
    </div>
    {error && <p className="error" role="alert">{error}</p>}
    <button className="primary" disabled={busy}>{busy ? 'Creating account…' : 'Create account & send OTPs'}</button>
    <p className="switch">Already registered? <button type="button" onClick={onLogin}>Sign in</button></p>
  </form>;
}

function VerifyForm({ registration, onComplete }: { registration: Registration; onComplete: () => void }) {
  const [emailOtp, setEmailOtp] = useState('');
  const [mobileOtp, setMobileOtp] = useState(registration.developmentMobileOtp ?? '');
  const [developmentMobileOtp, setDevelopmentMobileOtp] = useState(registration.developmentMobileOtp ?? '');
  const [verified, setVerified] = useState({ email: false, mobile: false });
  const [error, setError] = useState('');
  const [notice, setNotice] = useState(registration.deliveryWarning ?? '');
  const [busy, setBusy] = useState<'email' | 'mobile' | ''>('');

  async function verify(channel: 'email' | 'mobile', otp: string) {
    setBusy(channel); setError('');
    try { await verifyOtp(channel, registration.verificationSessionId, otp); setVerified((current) => ({ ...current, [channel]: true })); }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'The OTP could not be verified.'); }
    finally { setBusy(''); }
  }

  async function resend(channel: 'email' | 'mobile') {
    setBusy(channel); setError(''); setNotice('');
    try {
      const result = await resendVerificationOtp(channel === 'email' ? 'EMAIL' : 'MOBILE', registration.verificationSessionId);
      if (channel === 'mobile' && result.developmentOtp) {
        setMobileOtp(result.developmentOtp);
        setDevelopmentMobileOtp(result.developmentOtp);
      }
      setNotice(`${channel === 'email' ? 'Email' : 'Mobile'} OTP was reissued.`);
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'The OTP could not be resent.'); }
    finally { setBusy(''); }
  }

  const complete = verified.email && verified.mobile;
  return <section className="auth-form verification">
    <p className="eyebrow">DUAL VERIFICATION</p><h1>Verify both channels</h1><p className="lede">Codes expire in {Math.round(registration.expiresInSeconds / 60)} minutes.</p>
    <div className={verified.email ? 'otp-card complete' : 'otp-card'}><span className="channel">EMAIL</span><h2>{registration.maskedEmail}</h2><label>Six-digit email OTP<input inputMode="numeric" maxLength={6} value={emailOtp} onChange={(event) => setEmailOtp(event.target.value.replace(/\D/g, ''))} disabled={verified.email} /></label><div className="otp-actions"><button className="secondary" onClick={() => verify('email', emailOtp)} disabled={verified.email || busy !== '' || emailOtp.length !== 6}>{verified.email ? 'Email verified ✓' : busy === 'email' ? 'Working…' : 'Verify email'}</button><button className="resend" onClick={() => resend('email')} disabled={verified.email || busy !== ''}>Resend</button></div></div>
    <div className={verified.mobile ? 'otp-card complete' : 'otp-card'}><span className="channel">MOBILE · MOCK</span><h2>{registration.maskedMobileNumber}</h2><label>Six-digit mobile OTP<input inputMode="numeric" maxLength={6} value={mobileOtp} onChange={(event) => setMobileOtp(event.target.value.replace(/\D/g, ''))} disabled={verified.mobile} /></label>{registration.developmentMobileOtp && <small>Development OTP supplied by the mock SMS service.</small>}<div className="otp-actions"><button className="secondary" onClick={() => verify('mobile', mobileOtp)} disabled={verified.mobile || busy !== '' || mobileOtp.length !== 6}>{verified.mobile ? 'Mobile verified ✓' : busy === 'mobile' ? 'Working…' : 'Verify mobile'}</button><button className="resend" onClick={() => resend('mobile')} disabled={verified.mobile || busy !== ''}>Resend</button></div></div>
    {developmentMobileOtp && <p className="mock-otp">Mock mobile OTP: <strong>{developmentMobileOtp}</strong></p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    {error && <p className="error" role="alert">{error}</p>}
    <button className="primary" onClick={onComplete} disabled={!complete}>Continue to sign in</button>
  </section>;
}

function Workspace({ session, onSignedOut }: { session: AuthSession; onSignedOut: () => void }) {
  const [busy, setBusy] = useState(false);
  async function signOut() { setBusy(true); try { await logout(session.accessToken); } finally { onSignedOut(); } }
  return <main className="workspace"><header><Brand /><button className="secondary" onClick={signOut} disabled={busy}>Sign out</button></header><section className="welcome"><span className="success">✓</span><p className="eyebrow">IDENTITY VERIFIED</p><h1>Welcome, {session.user.displayName}</h1><p>Your email and mobile number are verified. Broker connection remains the next, separate integration phase.</p><div className="identity-summary"><span><small>EMAIL</small>{session.user.email}</span><span><small>MOBILE</small>{session.user.mobileNumber}</span></div></section></main>;
}

function BrokerWorkspace({ session, onSignedOut }: { session: AuthSession; onSignedOut: () => void }) {
  const [busy, setBusy] = useState(false);
  const [broker, setBroker] = useState<BrokerOverview>();
  const [brokerError, setBrokerError] = useState('');
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);

  useEffect(() => {
    let mounted = true;
    const refresh = () => loadBrokerOverview(session.accessToken)
      .then((overview) => { if (mounted) { setBroker(overview); setBrokerError(''); } })
      .catch((failure) => { if (mounted) setBrokerError(
        failure instanceof Error ? failure.message : 'Broker status is unavailable.'); });
    const release = () => { void releaseZerodhaBrowserLease(session.accessToken).catch(() => undefined); };
    void refresh();
    const heartbeat = window.setInterval(refresh, 30_000);
    window.addEventListener('pagehide', release);
    return () => {
      mounted = false;
      window.clearInterval(heartbeat);
      window.removeEventListener('pagehide', release);
    };
  }, [session.accessToken]);

  async function signOut() {
    setBusy(true);
    try {
      if (broker?.zerodhaConnected) await disconnectZerodhaConnection(session.accessToken);
    } catch {
      await releaseZerodhaBrowserLease(session.accessToken).catch(() => undefined);
    }
    try { await logout(session.accessToken); } finally { onSignedOut(); }
  }

  async function connectZerodha() {
    setBusy(true); setBrokerError('');
    try { window.location.href = (await beginZerodhaConnection(session.accessToken)).authorizationUrl; }
    catch (failure) {
      setBrokerError(failure instanceof Error ? failure.message : 'Zerodha connection could not start.');
      setBusy(false);
    }
  }

  async function disconnectZerodha() {
    setBusy(true); setBrokerError('');
    try {
      await disconnectZerodhaConnection(session.accessToken);
      setBroker(await loadBrokerOverview(session.accessToken));
      setConfirmDisconnect(false);
    } catch (failure) {
      setBrokerError(failure instanceof Error ? failure.message : 'Zerodha could not be disconnected.');
    } finally { setBusy(false); }
  }

  return <main className="workspace"><header><Brand /><button className="secondary" onClick={signOut} disabled={busy}>Sign out</button></header><section className="workspace-content">
    <div className="workspace-heading"><p className="eyebrow">TRADING WORKSPACE</p><h1>Welcome, {session.user.displayName}</h1><p>Connect market data, validate strategies in simulation, and keep live execution locked.</p></div>
    <div className="broker-grid">
      <article className="broker-card zerodha"><div className="card-top"><span className="broker-logo">Z</span><span className={broker?.zerodhaConnected ? 'status connected' : 'status'}>{broker?.zerodhaConnected ? 'CONNECTED' : broker?.zerodhaConfigured ? 'READY' : 'SETUP REQUIRED'}</span></div><h2>Zerodha</h2><p>Historical Kite Connect data for research and backtests.</p>{broker?.zerodhaConnected ? <><div className="connection-detail"><small>ACCOUNT</small><b>{broker.zerodhaAccountId}</b>{broker.zerodhaSessionExpiresAt && <small>Kite session expires {new Date(broker.zerodhaSessionExpiresAt).toLocaleString()}</small>}<small>Browser-bound session · closes automatically</small></div>{confirmDisconnect ? <div className="disconnect-confirm"><small>This logs out the Kite API session.</small><div><button className="secondary" onClick={() => setConfirmDisconnect(false)} disabled={busy}>Cancel</button><button className="danger" onClick={disconnectZerodha} disabled={busy}>{busy ? 'Disconnecting…' : 'Confirm disconnect'}</button></div></div> : <button className="disconnect" onClick={() => setConfirmDisconnect(true)} disabled={busy}>Disconnect Zerodha</button>}</> : <button className="primary" onClick={connectZerodha} disabled={busy || !broker?.zerodhaConfigured}>{broker?.zerodhaConfigured ? 'Connect Zerodha' : 'Configure API keys'}</button>}</article>
      <article className="broker-card"><div className="card-top"><span className="broker-logo paper">P</span><span className="status connected">AVAILABLE</span></div><h2>Paper Trading</h2><p>Risk-free simulated fills with isolated cash and positions.</p><div className="capability"><b>Market orders</b><span>Enabled</span></div></article>
      <article className="broker-card"><div className="card-top"><span className="broker-logo backtest">B</span><span className="status connected">PRIORITY</span></div><h2>Backtesting</h2><p>Replay Zerodha candles through Paper Trading before considering live execution.</p><div className="capability"><b>Chronological replay</b><span>Enabled</span></div></article>
    </div>
    {brokerError && <p className="error" role="alert">{brokerError}</p>}
    <div className="safety-banner"><span>LIVE</span><div><b>Live trading is locked</b><p>Execution remains unavailable until risk controls, audit persistence, and simulation quality gates are complete.</p></div></div>
    <div className="identity-summary"><span><small>VERIFIED EMAIL</small>{session.user.email}</span><span><small>VERIFIED MOBILE</small>{session.user.mobileNumber}</span></div>
  </section></main>;
}

export function App() {
  const [screen, setScreen] = useState<Screen>('login');
  const [registration, setRegistration] = useState<Registration>();
  const [session, setSession] = useState<AuthSession | undefined>(() => loadStoredSession());
  const authenticated = (value: AuthSession) => { storeSession(value); setSession(value); };
  const signedOut = () => { clearStoredSession(); setSession(undefined); };
  if (session) return <BrokerWorkspace session={session} onSignedOut={signedOut} />;
  return <main className="shell"><section className="story"><Brand /><div><p className="eyebrow">ACCOUNTABLE INTELLIGENCE</p><h2>Secure identity before broker connectivity.</h2><p>PredictiveEdge separates identity verification from broker authorization. Your broker credentials never enter this login flow.</p></div><ul><li><b>Amazon SES</b><span>Real email OTP delivery</span></li><li><b>Mock SMS</b><span>Safe development verification</span></li><li><b>Dual verification</b><span>Both channels required</span></li></ul></section><section className="panel">
    {screen === 'login' && <LoginForm onAuthenticated={authenticated} onRegister={() => setScreen('register')} />}
    {screen === 'register' && <RegisterForm onAccepted={(result) => { setRegistration(result); setScreen('verify'); }} onLogin={() => setScreen('login')} />}
    {screen === 'verify' && registration && <VerifyForm registration={registration} onComplete={() => setScreen('login')} />}
  </section></main>;
}
