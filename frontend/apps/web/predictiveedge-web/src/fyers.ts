export type FyersConnectionStatus = {
  configured: boolean;
  connected: boolean;
  accountId?: string;
  connectedAt?: string;
  expiresAt?: string;
  redirectUri: string;
};

async function request<T>(path: string, accessToken: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/fyers/v1${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(payload.message ?? `FYERS request failed (HTTP ${response.status}).`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const loadFyersConnection = (accessToken: string) => request<FyersConnectionStatus>('/connection', accessToken);
export const beginFyersConnection = (accessToken: string) => request<{ authorizationUrl: string }>('/connect', accessToken, { method: 'POST' });
export const disconnectFyersConnection = (accessToken: string) => request<void>('/connection', accessToken, { method: 'DELETE' });
