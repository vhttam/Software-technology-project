import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { setSession } from '../api/apiClient';
import { AuthProvider } from '../auth/AuthContext';
import { JobHistoryPage } from '../pages/JobHistoryPage';
import { JobDetailPage } from '../pages/JobDetailPage';
import { server } from './handlers';

function renderWithRouter(initialRoute = '/jobs') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialRoute]}>
        <Routes>
          <Route path="/jobs" element={<JobHistoryPage />} />
          <Route path="/jobs/:id" element={<JobDetailPage />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe('JobHistoryPage and Category 11 Not Found behavior', () => {
  it('renders task history list from GET /api/v1/jobs', async () => {
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
      http.get('*/api/v1/jobs', () => {
        return HttpResponse.json({
          status: 'success',
          data: {
            items: [
              {
                jobId: 'j-hist-1',
                contentId: 'c-1',
                contentTitle: 'Lịch sử Thăng Long',
                poiId: 'p-1',
                poiName: 'Hoàng thành Thăng Long',
                version: 1,
                status: 'COMPLETED',
                createdAt: '2026-10-16T10:00:00Z',
                targets: [{ targetId: 't-1', lang: 'en', voiceId: 'voice-1', status: 'PUBLISHED' }],
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

    renderWithRouter('/jobs');

    await waitFor(() => {
      expect(screen.getByText(/Lịch sử Thăng Long/i)).toBeInTheDocument();
      expect(screen.getByText(/Hoàng thành Thăng Long/i)).toBeInTheDocument();
      expect(screen.getByText(/Hoàn tất|COMPLETED/i)).toBeInTheDocument();
    });
  });

  it('does not display manual UUID lookup input on job history page', async () => {
    renderWithRouter('/jobs');
    expect(screen.queryByPlaceholderText(/Nhập mã tác vụ/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Tìm theo mã tác vụ/i)).not.toBeInTheDocument();
    expect(screen.getByText(/TIẾN TRÌNH THUYẾT MINH/i)).toBeInTheDocument();
  });

  it('displays not-found screen when navigating directly to non-existent job detail URL', async () => {
    server.use(
      http.get('*/api/v1/jobs/missing-job-id', () => {
        return HttpResponse.json(
          {
            status: 'error',
            errorCode: 'JOB_NOT_FOUND',
            message: 'Không tìm thấy job',
          },
          { status: 404 }
        );
      })
    );

    renderWithRouter('/jobs/missing-job-id');

    await waitFor(() => {
      expect(screen.getByText(/Không tìm thấy tác vụ/i)).toBeInTheDocument();
      expect(screen.getByText(/missing-job-id/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Quay lại danh sách tác vụ/i })).toBeInTheDocument();
    });
  });
});
