import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { ApiError } from '../api/types';
import { useAuth } from '../auth/AuthContext';

interface ChangeOwnPasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ChangeOwnPasswordModal: React.FC<ChangeOwnPasswordModalProps> = ({
  isOpen,
  onClose,
}) => {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!currentPassword) {
      setError('Vui lòng nhập mật khẩu hiện tại.');
      return;
    }
    if (newPassword.length < 8) {
      setError('Mật khẩu mới phải có tối thiểu 8 ký tự.');
      return;
    }
    if (newPassword === currentPassword) {
      setError('Mật khẩu mới phải khác mật khẩu hiện tại.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Nhập lại mật khẩu mới không khớp.');
      return;
    }

    setLoading(true);
    try {
      await authApi.changeOwnPassword({
        currentPassword,
        newPassword,
      });

      setSuccess(true);
      setTimeout(async () => {
        await logout();
        navigate('/login');
      }, 1500);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(`[${err.errorCode}] ${err.message}`);
      } else {
        setError('Không thể đổi mật khẩu. Vui lòng thử lại sau.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (!loading && !success) {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setError(null);
      onClose();
    }
  };

  return (
    <div className="modal-backdrop" onClick={handleClose}>
      <div
        className="modal-dialog"
        style={{ maxWidth: '480px' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h3>Đổi mật khẩu của tôi</h3>
          <button
            type="button"
            className="btn-link"
            onClick={handleClose}
            disabled={loading || success}
            style={{ fontSize: '18px', cursor: 'pointer' }}
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {error && <div className="alert alert-danger">{error}</div>}
            {success && (
              <div className="alert alert-success">
                Đổi mật khẩu thành công. Vui lòng đăng nhập lại với mật khẩu mới...
              </div>
            )}

            {!success && (
              <>
                <div className="form-group" style={{ marginBottom: '14px' }}>
                  <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '6px' }}>
                    Mật khẩu hiện tại <span style={{ color: 'red' }}>*</span>
                  </label>
                  <input
                    type="password"
                    autoComplete="current-password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    required
                    disabled={loading}
                    placeholder="Nhập mật khẩu đang sử dụng"
                    style={{ width: '100%', padding: '8px 12px', border: '1px solid var(--border-line)' }}
                  />
                </div>

                <div className="form-group" style={{ marginBottom: '14px' }}>
                  <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '6px' }}>
                    Mật khẩu mới (tối thiểu 8 ký tự) <span style={{ color: 'red' }}>*</span>
                  </label>
                  <input
                    type="password"
                    autoComplete="new-password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                    minLength={8}
                    disabled={loading}
                    placeholder="Nhập mật khẩu mới"
                    style={{ width: '100%', padding: '8px 12px', border: '1px solid var(--border-line)' }}
                  />
                </div>

                <div className="form-group" style={{ marginBottom: '14px' }}>
                  <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '6px' }}>
                    Nhập lại mật khẩu mới <span style={{ color: 'red' }}>*</span>
                  </label>
                  <input
                    type="password"
                    autoComplete="new-password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    minLength={8}
                    disabled={loading}
                    placeholder="Xác nhận lại mật khẩu mới"
                    style={{ width: '100%', padding: '8px 12px', border: '1px solid var(--border-line)' }}
                  />
                </div>
              </>
            )}
          </div>

          <div className="modal-footer">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleClose}
              disabled={loading || success}
            >
              Hủy
            </button>
            {!success && (
              <button
                type="submit"
                className="btn btn-primary"
                disabled={loading}
              >
                {loading ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
};
