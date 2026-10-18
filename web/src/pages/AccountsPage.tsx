import React, { useEffect, useState } from 'react';
import { authApi } from '../api/authApi';
import { ApiError, UserAccountItem } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/ErrorBanner';
import { Spinner } from '../components/Spinner';
import { SearchInput } from '../components/SearchInput';
import { Button } from '../components/Button';
import { PageHeader } from '../components/PageHeader';

export const AccountsPage: React.FC = () => {
  const { user: currentUser } = useAuth();

  const [users, setUsers] = useState<UserAccountItem[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [query, setQuery] = useState('');
  const [selectedRole, setSelectedRole] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [actionNotice, setActionNotice] = useState('');

  // Create form
  const [username, setUsername] = useState('');
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [creating, setCreating] = useState(false);

  // Subordinate password reset modal
  const [resettingUser, setResettingUser] = useState<UserAccountItem | null>(null);
  const [resetNewPassword, setResetNewPassword] = useState('');
  const [resetConfirmPassword, setResetConfirmPassword] = useState('');
  const [resettingPassword, setResettingPassword] = useState(false);
  const [resetError, setResetError] = useState<string | null>(null);

  // Disable confirmation modal
  const [disablingUser, setDisablingUser] = useState<UserAccountItem | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const loadUsers = async (
    targetPage = 1,
    searchQuery = query,
    roleFilter = selectedRole,
    statusFilter = selectedStatus
  ) => {
    setLoading(true);
    setError(null);
    try {
      const data = await authApi.listUsers(
        targetPage,
        10,
        searchQuery,
        roleFilter || undefined,
        statusFilter || undefined
      );
      setUsers(data.items || []);
      setPage(data.page);
      setTotalPages(data.totalPages || 1);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers(1);
  }, []);

  const handleSearchChange = (val: string) => {
    setQuery(val);
    loadUsers(1, val, selectedRole, selectedStatus);
  };

  const handleRoleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value;
    setSelectedRole(val);
    loadUsers(1, query, val, selectedStatus);
  };

  const handleStatusChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value;
    setSelectedStatus(val);
    loadUsers(1, query, selectedRole, val);
  };

  const handleResetFilters = () => {
    setQuery('');
    setSelectedRole('');
    setSelectedStatus('');
    loadUsers(1, '', '', '');
  };

  const hasFilters = Boolean(query || selectedRole || selectedStatus);

  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setCreating(true);

    try {
      const created = await authApi.createUser({
        username: username.trim(),
        name: name.trim(),
        password,
        role: 'CONTENT_ADMIN',
      });
      setActionNotice(`Đã tạo tài khoản thành công cho "${created.username}".`);
      setUsername('');
      setName('');
      setPassword('');
      await loadUsers(page);
    } catch (err) {
      if (err instanceof ApiError && err.errorCode === 'USERNAME_EXISTS') {
        setError(new ApiError(err.status, err.errorCode, 'Tên đăng nhập đã tồn tại trong hệ thống. Vui lòng chọn tên khác.', err.details, err.correlationId));
      } else {
        setError(err);
      }
    } finally {
      setCreating(false);
    }
  };

  const handleUnlock = async (u: UserAccountItem) => {
    setError(null);
    setActionLoading(true);
    try {
      await authApi.unlockUser(u.userId);
      setActionNotice(`Đã mở khóa tài khoản "${u.username}". Bộ đếm đăng nhập sai đã được đặt lại.`);
      await loadUsers(page);
    } catch (err) {
      setError(err);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDisableConfirm = async () => {
    if (!disablingUser) return;
    setError(null);
    setActionLoading(true);
    try {
      await authApi.disableUser(disablingUser.userId);
      setActionNotice(`Đã khóa vĩnh viễn tài khoản "${disablingUser.username}". Người dùng sẽ bị đăng xuất khỏi mọi thiết bị.`);
      setDisablingUser(null);
      await loadUsers(page);
    } catch (err) {
      setError(err);
    } finally {
      setActionLoading(false);
    }
  };

  const handleEnable = async (u: UserAccountItem) => {
    setError(null);
    setActionLoading(true);
    try {
      await authApi.enableUser(u.userId);
      setActionNotice(`Đã mở lại hoạt động cho tài khoản "${u.username}".`);
      await loadUsers(page);
    } catch (err) {
      setError(err);
    } finally {
      setActionLoading(false);
    }
  };

  const handleResetPasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resettingUser) return;
    setResetError(null);

    if (resetNewPassword.length < 8) {
      setResetError('Mật khẩu mới phải có tối thiểu 8 ký tự.');
      return;
    }
    if (resetNewPassword !== resetConfirmPassword) {
      setResetError('Nhập lại mật khẩu mới không khớp.');
      return;
    }

    setResettingPassword(true);
    try {
      await authApi.resetSubordinatePassword(resettingUser.userId, {
        newPassword: resetNewPassword,
      });
      setActionNotice(`Đã đặt lại mật khẩu cho "${resettingUser.username}". Người dùng sẽ bị đăng xuất khỏi mọi thiết bị.`);
      setResettingUser(null);
      setResetNewPassword('');
      setResetConfirmPassword('');
    } catch (err) {
      if (err instanceof ApiError) {
        setResetError(err.message);
      } else {
        setResetError('Không thể đặt lại mật khẩu. Vui lòng thử lại sau.');
      }
    } finally {
      setResettingPassword(false);
    }
  };

  const formatDate = (iso?: string | null) => {
    if (!iso) return '—';
    try {
      return new Date(iso).toLocaleString('vi-VN');
    } catch {
      return iso;
    }
  };

  const getEffectiveStatus = (u: UserAccountItem): 'ACTIVE' | 'LOCKED_TEMP' | 'DISABLED' => {
    if (u.status === 'DISABLED') return 'DISABLED';
    if (u.status === 'LOCKED_TEMP') return 'LOCKED_TEMP';
    const isLockedTime = u.lockedUntil ? new Date(u.lockedUntil) > new Date() : false;
    if (isLockedTime) return 'LOCKED_TEMP';
    return 'ACTIVE';
  };

  const renderStatusBadge = (u: UserAccountItem) => {
    const st = getEffectiveStatus(u);
    if (st === 'DISABLED') {
      return (
        <span
          className="status"
          style={{ backgroundColor: '#fee2e2', color: '#991b1b', border: '1px solid #fca5a5' }}
        >
          Khóa vĩnh viễn
        </span>
      );
    }
    if (st === 'LOCKED_TEMP') {
      return (
        <span className="status status-failed">
          Khóa tạm (đến {formatDate(u.lockedUntil)})
        </span>
      );
    }
    return <span className="status status-published">Đang hoạt động</span>;
  };

  return (
    <div className="admin-page" style={{ padding: '0 4px' }}>
      <PageHeader
        eyebrow="QUẢN TRỊ HỆ THỐNG"
        title="Quản trị tài khoản"
        description="Quản lý tài khoản quản trị viên nội dung, đặt lại mật khẩu và khóa hoặc mở khóa tài khoản."
        action={
          <Button
            variant="secondary"
            size="md"
            onClick={() => loadUsers(page)}
            icon={
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.3" />
              </svg>
            }
          >
            Tải lại
          </Button>
        }
      />

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {actionNotice && (
        <div
          style={{
            background: '#e8f2ea',
            border: '1px solid #b7d6be',
            borderRadius: '8px',
            padding: '12px 16px',
            margin: '14px 0',
            color: '#285e3a',
            fontSize: '13px',
          }}
        >
          ✓ {actionNotice}
        </div>
      )}

      <div className="manager-grid" style={{ marginTop: '20px' }}>
        {/* Create Form Panel */}
        <div
          className="panel"
          style={{
            background: '#ffffff',
            border: '1px solid var(--border-light)',
            borderRadius: '8px',
            padding: '20px 24px',
          }}
        >
          <div
            className="panel-heading"
            style={{
              marginBottom: '18px',
              borderBottom: '1px solid var(--border-light)',
              paddingBottom: '12px',
            }}
          >
            <div className="eyebrow" style={{ fontSize: '11px', fontWeight: 'bold', color: '#64748b' }}>
              TẠO MỚI
            </div>
            <h2 style={{ margin: '4px 0 0', fontSize: '17px', color: 'var(--text-main)' }}>
              Thêm tài khoản quản trị
            </h2>
          </div>

          <form
            className="stack-form"
            onSubmit={handleCreateUser}
            autoComplete="off"
            style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}
          >
            <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '14px', color: '#334155' }}>
              <span>Tên đăng nhập *</span>
              <input
                required
                maxLength={80}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Ví dụ: content_admin_1"
                disabled={creating}
                autoComplete="off"
                name="admin_new_username"
                style={{
                  height: '40px',
                  padding: '0 12px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '14px',
                  outline: 'none',
                }}
              />
            </label>

            <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '14px', color: '#334155' }}>
              <span>Họ và tên hiển thị *</span>
              <input
                required
                maxLength={200}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Ví dụ: Nguyễn Văn A"
                disabled={creating}
                autoComplete="off"
                name="admin_new_fullname"
                style={{
                  height: '40px',
                  padding: '0 12px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '14px',
                  outline: 'none',
                }}
              />
            </label>

            <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '14px', color: '#334155' }}>
              <span>Mật khẩu khởi tạo (tối thiểu 8 ký tự) *</span>
              <input
                type="password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                disabled={creating}
                autoComplete="new-password"
                name="admin_new_password"
                style={{
                  height: '40px',
                  padding: '0 12px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '14px',
                  outline: 'none',
                }}
              />
            </label>

            {/* Read-only Role Presentation (Category 6) */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <span style={{ fontSize: '14px', color: '#334155', fontWeight: 500 }}>Vai trò</span>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  padding: '10px 14px',
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '4px',
                  fontSize: '14px',
                  color: 'var(--text-main)',
                }}
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="#64748b"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                  <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
                <span><strong>Quản trị viên nội dung</strong> (CONTENT_ADMIN)</span>
              </div>
              <small style={{ color: '#64748b', marginTop: '2px', fontSize: '11px' }}>
                Hệ thống chỉ cho phép tạo tài khoản Quản trị viên nội dung. Tài khoản Quản trị viên cấp cao được khởi tạo tự động khi cài đặt.
              </small>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="md"
              loading={creating}
              disabled={creating}
              style={{ marginTop: '8px' }}
            >
              {creating ? 'Đang tạo…' : '+ Tạo tài khoản'}
            </Button>
          </form>
        </div>

        {/* Users list panel */}
        <div
          className="panel"
          style={{
            background: '#ffffff',
            border: '1px solid var(--border-light)',
            borderRadius: '8px',
            padding: '20px 24px',
          }}
        >
          <div
            className="panel-heading"
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
              marginBottom: '18px',
              borderBottom: '1px solid var(--border-light)',
              paddingBottom: '14px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div className="eyebrow" style={{ fontSize: '11px', fontWeight: 'bold', color: '#64748b' }}>
                  DANH SÁCH
                </div>
                <h2 style={{ margin: '4px 0 0', fontSize: '17px', color: 'var(--text-main)' }}>
                  Tài khoản hệ thống
                </h2>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
              <SearchInput
                value={query}
                onChange={handleSearchChange}
                placeholder="Tìm theo tên hoặc tên đăng nhập..."
                debounceMs={300}
                style={{ flex: 1, minWidth: '220px' }}
              />
              <select
                value={selectedRole}
                onChange={handleRoleChange}
                aria-label="Lọc theo vai trò"
                style={{
                  height: '38px',
                  padding: '0 10px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '13px',
                  color: 'var(--text-main)',
                  background: '#ffffff',
                  outline: 'none',
                }}
              >
                <option value="">Tất cả vai trò</option>
                <option value="SUPER_ADMIN">Quản trị viên cấp cao</option>
                <option value="CONTENT_ADMIN">Quản trị viên nội dung</option>
              </select>
              <select
                value={selectedStatus}
                onChange={handleStatusChange}
                aria-label="Lọc theo trạng thái"
                style={{
                  height: '38px',
                  padding: '0 10px',
                  borderRadius: '4px',
                  border: '1px solid #cbd5e0',
                  fontSize: '13px',
                  color: 'var(--text-main)',
                  background: '#ffffff',
                  outline: 'none',
                }}
              >
                <option value="">Tất cả trạng thái</option>
                <option value="ACTIVE">Đang hoạt động</option>
                <option value="LOCKED">Khóa tạm</option>
                <option value="DISABLED">Khóa vĩnh viễn</option>
              </select>
              {hasFilters && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={handleResetFilters}
                  style={{ height: '38px' }}
                >
                  Đặt lại
                </Button>
              )}
            </div>
          </div>

          {loading ? (
            <div style={{ padding: '60px 0', textAlign: 'center' }}>
              <Spinner />
              <p style={{ marginTop: '12px', color: '#64748b' }}>Đang tải danh sách tài khoản…</p>
            </div>
          ) : users.length === 0 ? (
            <div style={{ padding: '40px 16px', textAlign: 'center', color: '#64748b' }}>
              Chưa có tài khoản nào phù hợp.
            </div>
          ) : (
            <div className="user-list" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {users.map((u) => {
                const status = getEffectiveStatus(u);
                const isSuperAdmin = u.role === 'SUPER_ADMIN';
                const isSelf = u.userId === currentUser?.userId;
                const canManage = !isSuperAdmin && !isSelf;

                return (
                  <div
                    className="user-card"
                    key={u.userId}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      justifyContent: 'space-between',
                      padding: '14px 16px',
                      border: '1px solid var(--border-light)',
                      borderRadius: '6px',
                      backgroundColor: '#ffffff',
                      flexWrap: 'wrap',
                      gap: '12px',
                    }}
                  >
                    <div style={{ display: 'flex', gap: '12px', flex: 1, minWidth: '220px' }}>
                      <div
                        className="avatar"
                        style={{
                          width: '36px',
                          height: '36px',
                          borderRadius: '50%',
                          background: isSuperAdmin ? '#1e3a8a' : '#0284c7',
                          color: '#ffffff',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: 'bold',
                          flexShrink: 0,
                        }}
                      >
                        {u.name?.charAt(0) || 'U'}
                      </div>
                      <div className="user-info" style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                          <strong style={{ fontSize: '15px', color: 'var(--text-main)' }}>{u.name}</strong>
                          {renderStatusBadge(u)}
                        </div>
                        <div style={{ fontSize: '12px', color: '#475569', marginTop: '3px' }}>
                          Tên đăng nhập: <code>{u.username}</code> · Vai trò:{' '}
                          <b>{isSuperAdmin ? 'Quản trị viên cấp cao' : 'Quản trị viên nội dung'}</b>
                        </div>
                        <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
                          Số lần đăng nhập sai: {u.failedLoginCount}
                          {isSelf && ' · (Tài khoản của bạn)'}
                        </div>
                      </div>
                    </div>

                    <div className="actions-row" style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
                      {canManage && (
                        <>
                          {status === 'LOCKED_TEMP' && (
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => handleUnlock(u)}
                              disabled={actionLoading}
                            >
                              Mở khóa
                            </Button>
                          )}

                          {status === 'DISABLED' ? (
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => handleEnable(u)}
                              disabled={actionLoading}
                            >
                              Mở lại tài khoản
                            </Button>
                          ) : (
                            <Button
                              variant="danger"
                              size="sm"
                              onClick={() => setDisablingUser(u)}
                              disabled={actionLoading}
                            >
                              Khóa vĩnh viễn
                            </Button>
                          )}

                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => {
                              setResettingUser(u);
                              setResetNewPassword('');
                              setResetConfirmPassword('');
                              setResetError(null);
                            }}
                            disabled={actionLoading}
                          >
                            Đặt lại mật khẩu
                          </Button>
                        </>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="pagination" style={{ display: 'flex', justifyContent: 'center', gap: '12px', marginTop: '20px' }}>
              <Button
                variant="secondary"
                size="sm"
                disabled={page <= 1 || loading}
                onClick={() => loadUsers(page - 1)}
              >
                ← Trang trước
              </Button>
              <span style={{ fontSize: '13px', alignSelf: 'center', color: '#64748b' }}>
                Trang {page} / {totalPages}
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={page >= totalPages || loading}
                onClick={() => loadUsers(page + 1)}
              >
                Trang sau →
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* Subordinate Reset Password Modal */}
      {resettingUser && (
        <div className="modal-backdrop" onClick={() => !resettingPassword && setResettingUser(null)}>
          <div
            className="modal-dialog"
            style={{ maxWidth: '460px' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h3 style={{ margin: 0, fontSize: '17px', color: 'var(--text-main)' }}>Đặt lại mật khẩu</h3>
              <button
                type="button"
                className="btn-link"
                onClick={() => setResettingUser(null)}
                disabled={resettingPassword}
                style={{ fontSize: '18px', cursor: 'pointer', background: 'none', border: 'none' }}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleResetPasswordSubmit}>
              <div className="modal-body" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <p style={{ margin: 0, fontSize: '13px', color: 'var(--text-muted)' }}>
                  Đặt lại mật khẩu cho tài khoản: <strong>{resettingUser.username}</strong> ({resettingUser.name}).
                  Thao tác không yêu cầu mật khẩu cũ. Người dùng sẽ bị đăng xuất khỏi mọi thiết bị.
                </p>

                {resetError && <div className="alert alert-danger" style={{ color: '#b91c1c', fontSize: '13px' }}>{resetError}</div>}

                <div className="form-group" style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <label style={{ fontWeight: 600, fontSize: '13px', color: '#334155' }}>
                    Mật khẩu mới (tối thiểu 8 ký tự) <span style={{ color: 'red' }}>*</span>
                  </label>
                  <input
                    type="password"
                    autoComplete="new-password"
                    value={resetNewPassword}
                    onChange={(e) => setResetNewPassword(e.target.value)}
                    required
                    minLength={8}
                    disabled={resettingPassword}
                    placeholder="Nhập mật khẩu mới"
                    style={{ height: '38px', padding: '0 10px', borderRadius: '4px', border: '1px solid #cbd5e0' }}
                  />
                </div>

                <div className="form-group" style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <label style={{ fontWeight: 600, fontSize: '13px', color: '#334155' }}>
                    Nhập lại mật khẩu mới <span style={{ color: 'red' }}>*</span>
                  </label>
                  <input
                    type="password"
                    autoComplete="new-password"
                    value={resetConfirmPassword}
                    onChange={(e) => setResetConfirmPassword(e.target.value)}
                    required
                    minLength={8}
                    disabled={resettingPassword}
                    placeholder="Xác nhận lại mật khẩu mới"
                    style={{ height: '38px', padding: '0 10px', borderRadius: '4px', border: '1px solid #cbd5e0' }}
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', padding: '14px 20px', borderTop: '1px solid var(--border-light)' }}>
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => setResettingUser(null)}
                  disabled={resettingPassword}
                >
                  Hủy
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  size="sm"
                  loading={resettingPassword}
                  disabled={resettingPassword}
                >
                  Xác nhận đặt lại
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Disable confirmation dialog modal */}
      {disablingUser && (
        <div className="modal-backdrop" onClick={() => !actionLoading && setDisablingUser(null)}>
          <div
            className="modal-dialog"
            style={{ maxWidth: '460px' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h3 style={{ margin: 0, fontSize: '17px', color: '#b91c1c' }}>Xác nhận khóa vĩnh viễn</h3>
              <button
                type="button"
                className="btn-link"
                onClick={() => setDisablingUser(null)}
                disabled={actionLoading}
                style={{ fontSize: '18px', cursor: 'pointer', background: 'none', border: 'none' }}
              >
                ✕
              </button>
            </div>

            <div className="modal-body" style={{ padding: '20px' }}>
              <p style={{ margin: 0, fontSize: '14px', lineHeight: 1.5 }}>
                Bạn có chắc chắn muốn <strong>khóa vĩnh viễn</strong> tài khoản{' '}
                <code>{disablingUser.username}</code> ({disablingUser.name}) không?
              </p>
              <div
                style={{
                  background: '#fef2f2',
                  border: '1px solid #fee2e2',
                  borderRadius: '6px',
                  padding: '10px 12px',
                  color: '#991b1b',
                  fontSize: '12px',
                  marginTop: '12px',
                }}
              >
                ⚠ Người dùng sẽ bị đăng xuất khỏi mọi thiết bị và không thể đăng nhập cho đến khi được Quản trị viên cấp cao mở lại.
              </div>
            </div>

            <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', padding: '14px 20px', borderTop: '1px solid var(--border-light)' }}>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => setDisablingUser(null)}
                disabled={actionLoading}
              >
                Hủy bỏ
              </Button>
              <Button
                type="button"
                variant="danger"
                size="sm"
                loading={actionLoading}
                disabled={actionLoading}
                onClick={handleDisableConfirm}
              >
                Xác nhận khóa
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
