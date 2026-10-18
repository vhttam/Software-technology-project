import React, { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

export const LoginPage: React.FC = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);

    try {
      await login(username.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.errorCode === 'INVALID_CREDENTIALS') {
        // Hợp đồng quy định mã 401 INVALID_CREDENTIALS dùng chung cho cả sai thông tin và bị khóa
        setError(
          new ApiError(
            err.status,
            err.errorCode,
            'Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản đang bị khóa tạm thời (khóa 15 phút sau 5 lần nhập sai).',
            err.details,
            err.correlationId
          )
        );
      } else {
        setError(err);
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-screen">
      <div className="login-box">
        <div className="login-header">
          <div className="login-header-icon">◈</div>
          <h1>CỔNG QUẢN TRỊ NỘI DUNG</h1>
          <p>Hệ thống Thuyết minh Tự động Đa ngôn ngữ</p>
        </div>

        <ErrorBanner error={error} onDismiss={() => setError(null)} />

        <form onSubmit={handleSubmit}>
          <label>
            Tên đăng nhập
            <input
              type="text"
              required
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={busy}
              placeholder="admin"
            />
          </label>

          <label>
            Mật khẩu
            <input
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={busy}
              placeholder="Mật khẩu"
            />
          </label>

          <button
            type="submit"
            className="primary"
            style={{ width: '100%', marginTop: '8px' }}
            disabled={busy}
          >
            {busy ? 'Đang xác thực thông tin…' : 'Đăng nhập vào hệ thống'}
          </button>
        </form>

        <div className="login-footer-note">
          Yêu cầu tài khoản quyền <code>CONTENT_ADMIN</code>. Tài khoản sẽ tự động khóa 15 phút sau 5 lần nhập sai liên tiếp.
        </div>
      </div>
    </div>
  );
};
