import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { clearSession, setSession } from '../api/apiClient';
import { AuthProvider } from '../auth/AuthContext';
import { ProtectedRoute } from '../auth/ProtectedRoute';

describe('ProtectedRoute', () => {
  it('redirects to /login when user is not authenticated', () => {
    clearSession();

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route
              path="/protected"
              element={
                <ProtectedRoute>
                  <div>Nội dung được bảo vệ</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div>Trang đăng nhập</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText('Trang đăng nhập')).toBeInTheDocument();
    expect(screen.queryByText('Nội dung được bảo vệ')).not.toBeInTheDocument();
  });

  it('renders protected content when user is authenticated with proper role', () => {
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

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route
              path="/protected"
              element={
                <ProtectedRoute requiredRole="CONTENT_ADMIN">
                  <div>Nội dung quản trị viên</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText('Nội dung quản trị viên')).toBeInTheDocument();
  });

  it('renders 403 FORBIDDEN when user role does not match requiredRole', () => {
    setSession({
      accessToken: 'valid-token',
      refreshToken: 'valid-refresh',
      user: {
        userId: 'u-2',
        username: 'guest',
        name: 'Guest User',
        role: 'STAFF',
      },
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/admin-only']}>
          <Routes>
            <Route
              path="/admin-only"
              element={
                <ProtectedRoute requiredRole="CONTENT_ADMIN">
                  <div>Chỉ admin mới thấy</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/FORBIDDEN \(403\)/i)).toBeInTheDocument();
    expect(screen.queryByText('Chỉ admin mới thấy')).not.toBeInTheDocument();
  });
});
