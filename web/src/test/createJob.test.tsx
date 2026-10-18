import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { BrowserRouter } from 'react-router-dom';
import { setSession } from '../api/apiClient';
import { AuthProvider } from '../auth/AuthContext';
import { CreateJobPage } from '../pages/CreateJobPage';
import { server } from './handlers';

function renderCreateJobPage() {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <CreateJobPage />
      </BrowserRouter>
    </AuthProvider>
  );
}

describe('CreateJobPage', () => {
  it('sends Idempotency-Key header on submission and reuses it on network retry', async () => {
    setSession({
      accessToken: 'valid-token',
      refreshToken: 'valid-refresh',
      user: {
        userId: 'u-1',
        username: 'admin',
        name: 'Admin',
        role: 'CONTENT_ADMIN',
      },
    });

    // Provide mock active contents list
    server.use(
      http.get('*/api/v1/contents', () => {
        return HttpResponse.json({
          status: 'success',
          data: {
            items: [
              {
                contentId: 'c-test-1',
                poiId: 'p-1',
                poiName: 'Bảo tàng Lịch sử',
                version: 1,
                title: 'Giới thiệu hiện vật',
                sourceLang: 'vi',
                isDeleted: false,
              },
            ],
            page: 1,
            pageSize: 20,
            totalItems: 1,
            totalPages: 1,
          },
        });
      })
    );

    const receivedKeys: string[] = [];
    let shouldFailNetwork = true;

    server.use(
      http.post('*/api/v1/jobs', ({ request }) => {
        const key = request.headers.get('Idempotency-Key');
        if (key) receivedKeys.push(key);

        if (shouldFailNetwork) {
          shouldFailNetwork = false;
          // Simulate network failure
          return HttpResponse.error();
        }

        return HttpResponse.json(
          {
            status: 'success',
            data: {
              jobId: 'j-new-999',
              contentId: 'c-test-1',
              version: 1,
              status: 'PENDING',
              createdAt: '2026-10-16T10:00:00Z',
              targets: [{ targetId: 't-1', lang: 'en', voiceId: 'en-US-Journey-F', status: 'PENDING' }],
            },
          },
          { status: 202 }
        );
      })
    );

    const user = userEvent.setup();
    renderCreateJobPage();

    // Wait for dropdown to populate
    await waitFor(() => {
      expect(screen.getByRole('option', { name: /Giới thiệu hiện vật/i })).toBeInTheDocument();
    });

    // Select language EN
    const enCheckbox = await screen.findByRole('checkbox', { name: /EN/i });
    await user.click(enCheckbox);

    // Submit form (1st attempt -> network error)
    const submitBtn = screen.getByRole('button', { name: /(Gửi yêu cầu tạo thuyết minh|Bắt đầu tạo thuyết minh)/i });
    await user.click(submitBtn);

    // Wait for error banner
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText(/NETWORK_ERROR/i)).toBeInTheDocument();
    });

    expect(receivedKeys.length).toBe(1);
    const firstKey = receivedKeys[0];
    expect(firstKey).toBeTruthy();

    // Retry submission immediately (2nd attempt -> should reuse the exact same Idempotency-Key)
    await user.click(submitBtn);

    await waitFor(() => {
      expect(receivedKeys.length).toBe(2);
      expect(receivedKeys[1]).toBe(firstKey);
    });
  });
});
