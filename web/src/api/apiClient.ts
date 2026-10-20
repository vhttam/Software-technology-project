import {
  ApiError,
  ApiErrorEnvelope,
  ApiSuccessEnvelope,
  RefreshResponseData,
  UserSummary,
} from './types';

const SESSION_STORAGE_KEY = 'cnpm_web_session';

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  user: UserSummary;
}

// In-memory token storage (with sessionStorage backup for page reload)
let memorySession: AuthSession | null = null;
const authErrorListeners: Array<() => void> = [];

export function initSession(): AuthSession | null {
  if (memorySession) return memorySession;
  try {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (raw) {
      memorySession = JSON.parse(raw);
    }
  } catch {
    memorySession = null;
  }
  return memorySession;
}

export function getSession(): AuthSession | null {
  return memorySession || initSession();
}

export function setSession(session: AuthSession | null): void {
  memorySession = session;
  try {
    if (session) {
      sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    } else {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
    }
  } catch {
    // Ignore storage quota/security issues in private modes
  }
}

export function clearSession(): void {
  setSession(null);
}

export function onAuthError(listener: () => void): () => void {
  authErrorListeners.push(listener);
  return () => {
    const index = authErrorListeners.indexOf(listener);
    if (index >= 0) authErrorListeners.splice(index, 1);
  };
}

function notifyAuthError(): void {
  for (const listener of authErrorListeners) {
    try {
      listener();
    } catch {
      // Ignore listener errors
    }
  }
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  auth?: boolean;
}

export const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export async function apiClient<T>(
  endpoint: string,
  options: RequestOptions = {},
  isRetry = false
): Promise<T> {
  const url = `${BASE_URL}${endpoint}`;
  const headers = new Headers(options.headers || {});

  // Set JSON content-type if body is provided and not already specified
  if (options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json; charset=utf-8');
  }

  // Inject Authorization Bearer token unless explicitly disabled
  const session = getSession();
  if (options.auth !== false && session?.accessToken) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
  }

  const fetchInit: RequestInit = {
    ...options,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  };

  let response: Response;
  try {
    response = await fetch(url, fetchInit);
  } catch {
    throw new ApiError(
      0,
      'NETWORK_ERROR',
      'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng hoặc API Gateway.'
    );
  }

  // 204 No Content
  if (response.status === 204) {
    return undefined as unknown as T;
  }

  // 401 Unauthorized handling: attempt single-flight refresh
  if (
    response.status === 401 &&
    !isRetry &&
    session?.refreshToken &&
    !endpoint.includes('/auth/login') &&
    !endpoint.includes('/auth/refresh')
  ) {
    try {
      const refreshRes = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      });

      if (refreshRes.ok) {
        const refreshEnvelope = (await refreshRes.json()) as ApiSuccessEnvelope<RefreshResponseData>;
        const newSession: AuthSession = {
          ...session,
          accessToken: refreshEnvelope.data.accessToken,
        };
        setSession(newSession);
        // Retry original request with new token
        return await apiClient<T>(endpoint, options, true);
      }
    } catch {
      // Refresh network failure falls through to session clear
    }

    clearSession();
    notifyAuthError();
  }

  // Read body safely
  let jsonBody: unknown = null;
  try {
    jsonBody = await response.json();
  } catch {
    jsonBody = null;
  }

  if (!response.ok) {
    const errorBody = jsonBody as Partial<ApiErrorEnvelope> | null;
    const errorCode = errorBody?.errorCode || (response.status === 401 ? 'UNAUTHORIZED' : 'UNKNOWN_ERROR');
    const message = errorBody?.message || `Yêu cầu thất bại với mã lỗi HTTP ${response.status}.`;
    const details = errorBody?.details || null;
    const correlationId = errorBody?.correlationId;

    if (response.status === 401 && !endpoint.startsWith('/api/v1/client/')) {
      clearSession();
      notifyAuthError();
    }

    throw new ApiError(response.status, errorCode, message, details, correlationId);
  }

  const successBody = jsonBody as ApiSuccessEnvelope<T>;
  if (successBody && typeof successBody === 'object' && 'data' in successBody && successBody.data !== undefined) {
    return successBody.data;
  }
  return jsonBody as T;
}
