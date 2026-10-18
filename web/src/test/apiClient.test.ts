import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { apiClient, setSession } from '../api/apiClient';
import { ApiError } from '../api/types';
import { server } from './handlers';

describe('apiClient', () => {
  it('unwraps success envelope {status: "success", data} correctly', async () => {
    const data = await apiClient<{ status: string }>('/health', { auth: false });
    expect(data).toEqual({ status: 'UP' });
  });

  it('throws typed ApiError containing errorCode, message, details and correlationId on 4xx/5xx', async () => {
    server.use(
      http.get('*/api/v1/test-error', () => {
        return HttpResponse.json(
          {
            status: 'error',
            errorCode: 'VALIDATION_ERROR',
            message: 'Dữ liệu không hợp lệ.',
            details: { field: 'name', issue: 'must not be empty' },
            correlationId: 'cid-val-999',
          },
          { status: 400 }
        );
      })
    );

    await expect(apiClient('/api/v1/test-error', { auth: false })).rejects.toThrow(ApiError);

    try {
      await apiClient('/api/v1/test-error', { auth: false });
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      const apiErr = err as ApiError;
      expect(apiErr.status).toBe(400);
      expect(apiErr.errorCode).toBe('VALIDATION_ERROR');
      expect(apiErr.message).toBe('Dữ liệu không hợp lệ.');
      expect(apiErr.correlationId).toBe('cid-val-999');
      expect(apiErr.details).toEqual({ field: 'name', issue: 'must not be empty' });
    }
  });

  it('automatically refreshes token on 401 when refreshToken is available and retries request', async () => {
    setSession({
      accessToken: 'expired-access-token',
      refreshToken: 'mock-refresh-token',
      user: {
        userId: 'u-1',
        username: 'admin',
        name: 'Admin',
        role: 'CONTENT_ADMIN',
      },
    });

    let calls = 0;
    server.use(
      http.get('*/api/v1/protected-data', ({ request }) => {
        calls++;
        const authHeader = request.headers.get('Authorization');
        if (authHeader === 'Bearer mock-new-access-token') {
          return HttpResponse.json({
            status: 'success',
            data: { message: 'Refreshed successfully' },
          });
        }
        return HttpResponse.json(
          {
            status: 'error',
            errorCode: 'UNAUTHORIZED',
            message: 'Token expired',
            correlationId: 'cid-exp-1',
          },
          { status: 401 }
        );
      })
    );

    const result = await apiClient<{ message: string }>('/api/v1/protected-data');
    expect(result.message).toBe('Refreshed successfully');
    expect(calls).toBe(2);
  });

  it('clears session when 401 refresh fails and throws ApiError', async () => {
    setSession({
      accessToken: 'expired-token',
      refreshToken: 'invalid-refresh-token',
      user: {
        userId: 'u-1',
        username: 'admin',
        name: 'Admin',
        role: 'CONTENT_ADMIN',
      },
    });

    server.use(
      http.get('*/api/v1/protected-fail', () => {
        return HttpResponse.json(
          {
            status: 'error',
            errorCode: 'UNAUTHORIZED',
            message: 'Token expired',
          },
          { status: 401 }
        );
      })
    );

    await expect(apiClient('/api/v1/protected-fail')).rejects.toThrow(ApiError);
  });
});
