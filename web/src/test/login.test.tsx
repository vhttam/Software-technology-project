import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import { LoginPage } from '../pages/LoginPage';

function renderLoginPage() {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <LoginPage />
      </BrowserRouter>
    </AuthProvider>
  );
}

describe('LoginPage', () => {
  it('renders login form with username, password fields and submit button', () => {
    renderLoginPage();

    expect(screen.getByLabelText(/Tên đăng nhập/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Mật khẩu/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Đăng nhập/i })).toBeInTheDocument();
  });

  it('displays error banner with errorCode INVALID_CREDENTIALS and correlationId when login fails', async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByLabelText(/Tên đăng nhập/i), 'wrong_user');
    await user.type(screen.getByLabelText(/Mật khẩu/i), 'wrong_password');
    await user.click(screen.getByRole('button', { name: /Đăng nhập/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText(/INVALID_CREDENTIALS/i)).toBeInTheDocument();
      expect(screen.getByText(/cid-login-fail-1/i)).toBeInTheDocument();
    });
  });

  it('submits successfully with valid credentials', async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByLabelText(/Tên đăng nhập/i), 'admin');
    await user.type(screen.getByLabelText(/Mật khẩu/i), 'password123');
    await user.click(screen.getByRole('button', { name: /Đăng nhập/i }));

    await waitFor(() => {
      // Upon successful login, error banner should not be present
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
  });
});
