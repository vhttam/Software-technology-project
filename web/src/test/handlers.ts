import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

export const handlers = [
  // Health
  http.get('*/health', () => {
    return HttpResponse.json({
      status: 'success',
      data: { status: 'UP' },
    });
  }),

  // Auth: Login
  http.post('*/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as { username?: string; password?: string };
    if (body.username === 'admin' && body.password === 'password123') {
      return HttpResponse.json({
        status: 'success',
        data: {
          accessToken: 'mock-access-token',
          refreshToken: 'mock-refresh-token',
          tokenType: 'Bearer',
          expiresIn: 900,
          refreshExpiresIn: 604800,
          user: {
            userId: 'u-admin-1',
            username: 'admin',
            name: 'Quản trị viên',
            role: 'CONTENT_ADMIN',
          },
        },
      });
    }

    return HttpResponse.json(
      {
        status: 'error',
        errorCode: 'INVALID_CREDENTIALS',
        message: 'Thông tin đăng nhập không chính xác hoặc tài khoản bị khóa.',
        details: null,
        correlationId: 'cid-login-fail-1',
      },
      { status: 401 }
    );
  }),

  // Auth: Refresh
  http.post('*/api/v1/auth/refresh', async ({ request }) => {
    const body = (await request.json()) as { refreshToken?: string };
    if (body.refreshToken === 'mock-refresh-token') {
      return HttpResponse.json({
        status: 'success',
        data: {
          accessToken: 'mock-new-access-token',
          tokenType: 'Bearer',
          expiresIn: 900,
        },
      });
    }

    return HttpResponse.json(
      {
        status: 'error',
        errorCode: 'INVALID_REFRESH_TOKEN',
        message: 'Refresh token không hợp lệ hoặc đã hết hạn.',
        details: null,
        correlationId: 'cid-refresh-fail-1',
      },
      { status: 401 }
    );
  }),

  // Auth: Logout
  http.post('*/api/v1/auth/logout', () => {
    return new HttpResponse(null, { status: 204 });
  }),

  // Narration: Voices
  http.get('*/api/v1/voices', () => {
    return HttpResponse.json({
      status: 'success',
      data: {
        supportedVoices: [
          { lang: 'en', voices: ['en-US-Journey-F', 'en-GB-Standard-A'] },
          { lang: 'fr', voices: ['fr-FR-Standard-A'] },
        ],
      },
    });
  }),

  // Narration: Create Job
  http.post('*/api/v1/jobs', async ({ request }) => {
    const idempotencyKey = request.headers.get('Idempotency-Key');
    if (!idempotencyKey) {
      return HttpResponse.json(
        {
          status: 'error',
          errorCode: 'IDEMPOTENCY_KEY_REQUIRED',
          message: 'Yêu cầu tạo Job bắt buộc phải có Idempotency-Key.',
          details: null,
          correlationId: 'cid-key-missing',
        },
        { status: 400 }
      );
    }

    const body = (await request.json()) as { contentId: string; targets: Array<{ lang: string; voiceId: string }> };
    return HttpResponse.json(
      {
        status: 'success',
        data: {
          jobId: 'j-new-123',
          contentId: body.contentId,
          version: 1,
          status: 'PENDING',
          createdAt: '2026-10-16T10:00:00Z',
          targets: (body.targets || []).map((t, idx) => ({
            targetId: `t-${idx + 1}`,
            lang: t.lang,
            voiceId: t.voiceId,
            status: 'PENDING',
          })),
        },
      },
      { status: 202 }
    );
  }),

  // Narration: Get Job
  http.get('*/api/v1/jobs/:jobId', ({ params }) => {
    const { jobId } = params;
    return HttpResponse.json({
      status: 'success',
      data: {
        jobId,
        contentId: 'c-test-1',
        version: 1,
        status: 'PROCESSING',
        createdAt: '2026-10-16T10:00:00Z',
        updatedAt: '2026-10-16T10:00:05Z',
        targets: [
          {
            targetId: 't-1',
            lang: 'en',
            voiceId: 'en-US-Journey-F',
            status: 'TRANSLATING',
            errorCode: null,
          },
        ],
      },
    });
  }),

  // Narration: Cancel Job
  http.post('*/api/v1/jobs/:jobId/cancel', ({ params }) => {
    const { jobId } = params;
    return HttpResponse.json({
      status: 'success',
      data: {
        jobId,
        contentId: 'c-test-1',
        version: 1,
        status: 'CANCELLED',
        createdAt: '2026-10-16T10:00:00Z',
        updatedAt: '2026-10-16T10:00:10Z',
        targets: [
          {
            targetId: 't-1',
            lang: 'en',
            voiceId: 'en-US-Journey-F',
            status: 'CANCELLED',
            errorCode: null,
          },
        ],
      },
    });
  }),
];

export const server = setupServer(...handlers);
