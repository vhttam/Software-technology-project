import React, { useState } from 'react';
import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ChangeOwnPasswordModal } from './ChangeOwnPasswordModal';

export const Layout: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const getRoleDisplayName = (role?: string) => {
    if (role === 'SUPER_ADMIN') return 'Quản trị viên cấp cao';
    if (role === 'CONTENT_ADMIN') return 'Quản trị viên nội dung';
    return role || 'Người dùng';
  };

  const isJobsListActive =
    location.pathname === '/jobs' ||
    (location.pathname.startsWith('/jobs/') && location.pathname !== '/jobs/new');
  const isCreateJobActive = location.pathname === '/jobs/new';

  return (
    <div className="admin-layout">
      {isMobileMenuOpen && (
        <div
          className="sidebar-backdrop"
          onClick={() => setIsMobileMenuOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Sidebar on the Left */}
      <aside className={`admin-sidebar ${isMobileMenuOpen ? 'open' : ''}`}>
        <div className="sidebar-brand">
          <div className="sidebar-brand-icon">◈</div>
          <div className="sidebar-brand-text">
            <strong>Thuyết minh tự động</strong>
            <small>Cổng thông tin quản trị</small>
          </div>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-group-title">DANH MỤC CHÍNH</div>

          <NavLink
            to="/"
            end
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setIsMobileMenuOpen(false)}
          >
            <span className="sidebar-icon">📊</span>
            <span>Tổng quan</span>
          </NavLink>

          <NavLink
            to="/pois"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setIsMobileMenuOpen(false)}
          >
            <span className="sidebar-icon">🏛</span>
            <span>Điểm tham quan</span>
          </NavLink>

          <NavLink
            to="/contents"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => setIsMobileMenuOpen(false)}
          >
            <span className="sidebar-icon">📄</span>
            <span>Nội dung thuyết minh</span>
          </NavLink>

          <div className="nav-group-title" style={{ marginTop: '16px' }}>
            TÁC VỤ XỬ LÝ
          </div>

          <NavLink
            to="/jobs/new"
            className={() => `sidebar-link ${isCreateJobActive ? 'active' : ''}`}
            onClick={() => setIsMobileMenuOpen(false)}
          >
            <span className="sidebar-icon">⚡</span>
            <span>Tạo tác vụ mới</span>
          </NavLink>

          <NavLink
            to="/jobs"
            className={() => `sidebar-link ${isJobsListActive ? 'active' : ''}`}
            onClick={() => setIsMobileMenuOpen(false)}
          >
            <span className="sidebar-icon">🕒</span>
            <span>Lịch sử tác vụ</span>
          </NavLink>

          {user?.role === 'SUPER_ADMIN' && (
            <>
              <div className="nav-group-title" style={{ marginTop: '16px' }}>
                HỆ THỐNG
              </div>

              <NavLink
                to="/accounts"
                className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
                onClick={() => setIsMobileMenuOpen(false)}
              >
                <span className="sidebar-icon">👥</span>
                <span>Quản trị tài khoản</span>
              </NavLink>
            </>
          )}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-version">Phiên bản giao diện: 1.0</div>
        </div>
      </aside>

      {/* Main Container with Topbar and Content */}
      <div className="admin-main-wrap">
        <header className="admin-topbar">
          <div className="topbar-left" style={{ display: 'flex', alignItems: 'center' }}>
            <button
              type="button"
              className="sidebar-toggle-btn"
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              style={{
                display: 'none',
                background: 'none',
                border: '1px solid #cbd5e1',
                borderRadius: '4px',
                padding: '6px 8px',
                cursor: 'pointer',
                marginRight: '8px',
                fontSize: '16px',
                lineHeight: 1,
              }}
              aria-label="Mở danh mục điều hướng"
            >
              ☰
            </button>
            <span className="topbar-system-name">Hệ thống Thuyết minh Tự động Đa ngôn ngữ</span>
          </div>

          <div className="topbar-right" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div className="user-profile">
              <span className="user-avatar">{user?.name ? user.name[0].toUpperCase() : 'A'}</span>
              <div className="user-details">
                <span className="user-name">{user?.name || user?.username || 'Quản trị viên'}</span>
                <span className="user-role-badge">{getRoleDisplayName(user?.role)}</span>
              </div>
            </div>

            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => setShowChangePassword(true)}
              title="Đổi mật khẩu tài khoản hiện tại"
            >
              Đổi mật khẩu
            </button>

            <button
              type="button"
              className="logout-button btn-sm"
              onClick={handleLogout}
              title="Đăng xuất khỏi hệ thống"
            >
              Đăng xuất
            </button>
          </div>
        </header>

        <main className="admin-main-content">
          <Outlet />
        </main>
      </div>

      <ChangeOwnPasswordModal
        isOpen={showChangePassword}
        onClose={() => setShowChangePassword(false)}
      />
    </div>
  );
};
