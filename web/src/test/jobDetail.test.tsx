import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { setSession } from '../api/apiClient';
import { AuthProvider } from '../auth/AuthContext';
import { JobDetailPage } from '../pages/JobDetailPage';
import { server } from './handlers';

function renderJobDetailPage(jobId = 'j-test-123') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[`/jobs/${jobId}`]}>
        <Routes>
          <Route path="/jobs/:id" element={<JobDetailPage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe('JobDetailPage', () => {
  it('enables cancel button when job status is PENDING or PROCESSING', async () => {
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

    server.use(
      http.get('*/api/v1/jobs/:jobId', () => {
        return HttpResponse.json({
          status: 'success',
          data: {
            jobId: 'j-test-123',
            contentId: 'c-1',
            version: 1,
            status: 'PROCESSING',
            createdAt: '2026-10-16T10:00:00Z',
            targets: [{ targetId: 't-1', lang: 'en', voiceId: 'en-US-Journey-F', status: 'TRANSLATING' }],
          },
        });
      })
    );

    renderJobDetailPage('j-test-123');

    await waitFor(() => {
      const cancelBtn = screen.getByRole('button', { name: /Hủy (Job|tác vụ) này/i });
      expect(cancelBtn).toBeInTheDocument();
      expect(cancelBtn).toBeEnabled();
    });
  });

  it('triggers cancel request when clicking cancel button', async () => {
    let cancelCalled = false;
    server.use(
      http.post('*/api/v1/jobs/:jobId/cancel', () => {
        cancelCalled = true;
        return HttpResponse.json({
          status: 'success',
          data: {
            jobId: 'j-test-123',
            contentId: 'c-1',
            version: 1,
            status: 'CANCELLED',
            createdAt: '2026-10-16T10:00:00Z',
            targets: [{ targetId: 't-1', lang: 'en', voiceId: 'en-US-Journey-F', status: 'CANCELLED' }],
          },
        });
      })
    );

    window.confirm = () => true; // auto-confirm confirm modal
    const user = userEvent.setup();
    renderJobDetailPage('j-test-123');

    const cancelBtn = await screen.findByRole('button', { name: /Hủy (Job|tác vụ) này/i });
    await user.click(cancelBtn);

    await waitFor(() => {
      expect(cancelCalled).toBe(true);
    });
  });

  it('does NOT display cancel button when job status is COMPLETED or CANCELLED', async () => {
    server.use(
      http.get('*/api/v1/jobs/:jobId', () => {
        return HttpResponse.json({
          status: 'success',
          data: {
            jobId: 'j-completed-123',
            contentId: 'c-1',
            version: 1,
            status: 'COMPLETED',
            createdAt: '2026-10-16T10:00:00Z',
            targets: [{ targetId: 't-1', lang: 'en', voiceId: 'en-US-Journey-F', status: 'PUBLISHED' }],
          },
        });
      })
    );

    renderJobDetailPage('j-completed-123');

    await waitFor(() => {
      expect(screen.getByText(/Sẵn sàng|PUBLISHED/i)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Hủy (Job|tác vụ) này/i })).not.toBeInTheDocument();
    });
  });
});
