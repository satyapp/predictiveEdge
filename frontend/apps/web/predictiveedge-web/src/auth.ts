export type User = {
  id: string;
  email: string;
  mobileNumber: string;
  displayName: string;
  state: string;
  emailVerified: boolean;
  mobileVerified: boolean;
};

export type Registration = {
  message: string;
  verificationSessionId: string;
  requiredChannels: ['EMAIL', 'MOBILE'];
  maskedEmail: string;
  maskedMobileNumber: string;
  developmentMobileOtp?: string;
  expiresInSeconds: number;
  deliveryWarning?: string;
};

export type AuthSession = {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  sessionId: string;
  user: User;
};

type ErrorPayload = { message?: string; fieldErrors?: { field: string; message?: string }[] };

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`/api/identity/v1${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as ErrorPayload;
    const fieldMessage = payload.fieldErrors?.find((field) => field.message)?.message;
    throw new Error(fieldMessage ?? payload.message ?? 'The request could not be completed.');
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function register(input: { displayName: string; email: string; mobileNumber: string; password: string }) {
  return request<Registration>('/auth/register', { method: 'POST', body: JSON.stringify(input) });
}

export function verifyOtp(channel: 'email' | 'mobile', verificationSessionId: string, otp: string) {
  return request<void>(`/auth/verify-${channel}`, {
    method: 'POST',
    body: JSON.stringify({ verificationSessionId, otp }),
  });
}

export function resendVerificationOtp(channel: 'EMAIL' | 'MOBILE', verificationSessionId: string) {
  return request<{ developmentOtp?: string; expiresInSeconds: number }>('/auth/verification/otp/resend', {
    method: 'POST',
    body: JSON.stringify({ verificationSessionId, channel }),
  });
}

export function login(email: string, password: string) {
  return request<AuthSession>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
}

export function logout(accessToken: string) {
  return request<void>('/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${accessToken}` } });
}

export function toIndianE164(value: string) {
  const digits = value.replace(/\D/g, '');
  const national = digits.startsWith('91') && digits.length > 10 ? digits.slice(2) : digits;
  return national.length === 10 ? `+91${national}` : value.trim();
}
