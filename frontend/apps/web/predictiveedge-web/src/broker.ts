export type BrokerOverview = {
  zerodhaConfigured: boolean;
  zerodhaConnected: boolean;
  zerodhaAccountId?: string;
  zerodhaConnectedAt?: string;
  zerodhaSessionExpiresAt?: string;
  browserLeaseExpiresAt?: string;
  paperTradingAvailable: boolean;
  backtestingAvailable: boolean;
  liveTradingEnabled: boolean;
};

async function brokerRequest<T>(path: string, accessToken: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/broker/v1${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(payload.message ?? 'The broker request could not be completed.');
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function loadBrokerOverview(accessToken: string) {
  return brokerRequest<BrokerOverview>('/connections', accessToken);
}

export function beginZerodhaConnection(accessToken: string) {
  return brokerRequest<{ authorizationUrl: string }>('/zerodha/connect', accessToken, { method: 'POST' });
}

export function disconnectZerodhaConnection(accessToken: string) {
  return brokerRequest<void>('/zerodha/connection', accessToken, { method: 'DELETE' });
}

export function releaseZerodhaBrowserLease(accessToken: string) {
  return fetch('/api/broker/v1/zerodha/lease/release', {
    method: 'POST',
    keepalive: true,
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
  });
}
