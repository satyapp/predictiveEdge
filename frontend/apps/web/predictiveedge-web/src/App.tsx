import { FormEvent, useState } from 'react';
import { AuthSession, login, logout, register, Registration, resendVerificationOtp, toIndianE164, verifyOtp } from './auth';

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

export function App() {
  const [screen, setScreen] = useState<Screen>('login');
  const [registration, setRegistration] = useState<Registration>();
  const [session, setSession] = useState<AuthSession>();
  if (session) return <Workspace session={session} onSignedOut={() => setSession(undefined)} />;
  return <main className="shell"><section className="story"><Brand /><div><p className="eyebrow">ACCOUNTABLE INTELLIGENCE</p><h2>Secure identity before broker connectivity.</h2><p>PredictiveEdge separates identity verification from broker authorization. Your broker credentials never enter this login flow.</p></div><ul><li><b>Amazon SES</b><span>Real email OTP delivery</span></li><li><b>Mock SMS</b><span>Safe development verification</span></li><li><b>Dual verification</b><span>Both channels required</span></li></ul></section><section className="panel">
    {screen === 'login' && <LoginForm onAuthenticated={setSession} onRegister={() => setScreen('register')} />}
    {screen === 'register' && <RegisterForm onAccepted={(result) => { setRegistration(result); setScreen('verify'); }} onLogin={() => setScreen('login')} />}
    {screen === 'verify' && registration && <VerifyForm registration={registration} onComplete={() => setScreen('login')} />}
  </section></main>;
}
