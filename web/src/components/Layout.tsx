import React, { useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ChangeOwnPasswordModal } from './ChangeOwnPasswordModal';

export const Layout: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [activeVisitors, setActiveVisitors] = useState<number>(0);

  useEffect(() => {
    // If user opened visitor page in the same browser session before switching to Admin,
    // clear visitor presence immediately so Admin users are NEVER counted in visitor metrics.
    const lingeringVisitorId = sessionStorage.getItem('visitor_id');
    if (lingeringVisitorId) {
      sessionStorage.removeItem('visitor_id');
      try {
        fetch('/api/v1/client/presence/heartbeat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ visitorId: lingeringVisitorId, leave: true }),
          keepalive: true,
        }).catch(() => {});
      } catch (_) {}
    }

    const fetchPresence = () => {
      fetch('/api/v1/client/presence/count')
        .then((res) => res.json())
        .then((json) => {
          if (json && json.data && typeof json.data.activeVisitors === 'number') {
            setActiveVisitors(json.data.activeVisitors);
          }
        })
        .catch(() => {});
    };
    fetchPresence();
    const interval = setInterval(fetchPresence, 10000);
    return () => clearInterval(interval);
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const getRoleDisplayName = (role?: string) => {
    if (role === 'SUPER_ADMIN') return 'Quản trị viên cấp cao';
    if (role === 'CONTENT_ADMIN') return 'Quản trị viên nội dung';
    return role || 'Người dùng';
  };

  const currentPath = location.pathname.replace(/\/+$/, '') || '/';
  const isOverviewActive =
    currentPath === '/' ||
    currentPath === '/admin' ||
    currentPath === '/admin/dashboard' ||
    currentPath === '/admin/overview' ||
    currentPath === '/admin.html' ||
    currentPath === '/dashboard' ||
    currentPath === '/overview';
  const isPoisActive = currentPath === '/pois' || currentPath === '/admin/pois';
  const isContentsActive = currentPath === '/contents' || currentPath === '/admin/contents';
  const isJobsListActive =
    currentPath === '/jobs' ||
    currentPath === '/admin/jobs' ||
    ((currentPath.startsWith('/jobs/') || currentPath.startsWith('/admin/jobs/')) &&
      currentPath !== '/jobs/new' &&
      currentPath !== '/admin/jobs/new');
  const isCreateJobActive = currentPath === '/jobs/new' || currentPath === '/admin/jobs/new';
  const isAccountsActive = currentPath === '/accounts' || currentPath === '/admin/accounts';

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
            className={() => `sidebar-link ${isOverviewActive ? 'active' : ''}`}
            onClick={() => setIsMobileMenuOpen(false)}
          >
            <span className="sidebar-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="3" width="7" height="9"></rect>
                <rect x="14" y="3" width="7" height="5"></rect>
                <rect x="14" y="12" width="7" height="9"></rect>
                <rect x="3" y="16" width="7" height="5"></rect>
              </svg>
            </span>
            <span>Tổng quan</span>
          </NavLink>

          <NavLink
            to="/pois"
            className={() => `sidebar-link ${isPoisActive ? 'active' : ''}`}
            onClick={() => setIsMobileMenuOpen(false)}
          >
            <span className="sidebar-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                <circle cx="12" cy="10" r="3"></circle>
              </svg>
            </span>
            <span>Điểm tham quan</span>
          </NavLink>

          <NavLink
            to="/contents"
            className={() => `sidebar-link ${isContentsActive ? 'active' : ''}`}
            onClick={() => setIsMobileMenuOpen(false)}
          >
            <span className="sidebar-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
                <line x1="16" y1="13" x2="8" y2="13"></line>
                <line x1="16" y1="17" x2="8" y2="17"></line>
                <polyline points="10 9 9 9 8 9"></polyline>
              </svg>
            </span>
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
            <span className="sidebar-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
              </svg>
            </span>
            <span>Tạo tác vụ mới</span>
          </NavLink>

          <NavLink
            to="/jobs"
            className={() => `sidebar-link ${isJobsListActive ? 'active' : ''}`}
            onClick={() => setIsMobileMenuOpen(false)}
          >
            <span className="sidebar-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10"></circle>
                <polyline points="12 6 12 12 16 14"></polyline>
              </svg>
            </span>
            <span>Lịch sử tác vụ</span>
          </NavLink>

          {user?.role === 'SUPER_ADMIN' && (
            <>
              <div className="nav-group-title" style={{ marginTop: '16px' }}>
                HỆ THỐNG
              </div>

              <NavLink
                to="/accounts"
                className={() => `sidebar-link ${isAccountsActive ? 'active' : ''}`}
                onClick={() => setIsMobileMenuOpen(false)}
              >
                <span className="sidebar-icon">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                    <circle cx="9" cy="7" r="4"></circle>
                    <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                    <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                  </svg>
                </span>
                <span>Quản trị tài khoản</span>
              </NavLink>
            </>
          )}
        </nav>

        <div className="sidebar-footer">
          <a
            href="/index.html"
            target="_blank"
            rel="noreferrer"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '8px 12px',
              backgroundColor: '#1e293b',
              color: '#94a3b8',
              borderRadius: '6px',
              textDecoration: 'none',
              fontSize: '12px',
              fontWeight: 600,
              marginBottom: '10px',
              border: '1px solid #334155',
            }}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
              <line x1="12" y1="18" x2="12.01" y2="18"></line>
            </svg>
            <span>Trang Khách tham quan ↗</span>
          </a>
          <div className="sidebar-version">Phiên bản giao diện: 2.0</div>
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
            {/* Live Visitors Real-time badge */}
            <div
              title="Số khách đang truy cập trang web (cập nhật tự động mỗi 15 giây)"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                backgroundColor: '#f0fdf4',
                border: '1px solid #bbf7d0',
                padding: '4px 10px',
                borderRadius: '20px',
                fontSize: '13px',
                color: '#166534',
                fontWeight: 600,
              }}
            >
              <span
                style={{
                  width: '8px',
                  height: '8px',
                  borderRadius: '50%',
                  backgroundColor: '#22c55e',
                  display: 'inline-block',
                  boxShadow: '0 0 6px #22c55e',
                }}
              />
              <span>{activeVisitors} khách trực tuyến</span>
            </div>

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
