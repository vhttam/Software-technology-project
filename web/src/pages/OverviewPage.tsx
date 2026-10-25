import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { contentApi } from '../api/contentApi';
import { authApi } from '../api/authApi';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/ErrorBanner';
import { Spinner } from '../components/Spinner';
import { PageHeader } from '../components/PageHeader';

interface OverviewStats {
  totalPois: number;
  totalContents: number;
  totalUsers: number | null;
  lockedUsers: number | null;
  disabledUsers: number | null;
  activeVisitors: number;
}

export const OverviewPage: React.FC = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState<OverviewStats>({
    totalPois: 0,
    totalContents: 0,
    totalUsers: null,
    lockedUsers: null,
    disabledUsers: null,
    activeVisitors: 1,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const loadMetrics = async () => {
    setLoading(true);
    setError(null);
    try {
      const [poisRes, contentsRes, presenceRes] = await Promise.all([
        contentApi.listPois(1, 1).catch(() => ({ totalItems: 0 })),
        contentApi.listContents(1, 1).catch(() => ({ totalItems: 0 })),
        fetch('/api/v1/client/presence/count')
          .then((r) => r.json())
          .then((j) => (j && j.data && typeof j.data.activeVisitors === 'number' ? j.data.activeVisitors : 0))
          .catch(() => 0),
      ]);

      let totalUsers: number | null = null;
      let lockedUsers: number | null = null;
      let disabledUsers: number | null = null;

      if (user?.role === 'SUPER_ADMIN') {
        try {
          const [usersAll, usersLocked] = await Promise.all([
            authApi.listUsers(1, 100),
            authApi.listUsers(1, 100, undefined, undefined, 'LOCKED'),
          ]);
          totalUsers = usersAll.totalItems;
          lockedUsers = usersLocked.totalItems;
          disabledUsers = (usersAll.items || []).filter(u => u.status === 'DISABLED').length;
        } catch {
          // If unauthorized or error, keep null
        }
      }

      setStats({
        totalPois: (poisRes && typeof poisRes === 'object' && 'totalItems' in poisRes ? (poisRes.totalItems as number) : 0) || 0,
        totalContents: (contentsRes && typeof contentsRes === 'object' && 'totalItems' in contentsRes ? (contentsRes.totalItems as number) : 0) || 0,
        totalUsers,
        lockedUsers,
        disabledUsers,
        activeVisitors: typeof presenceRes === 'number' ? presenceRes : 0,
      });
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMetrics();

    // Poll active visitor count every 15s
    const timer = setInterval(() => {
      fetch('/api/v1/client/presence/count')
        .then((r) => r.json())
        .then((j) => {
          if (j && j.data && typeof j.data.activeVisitors === 'number') {
            setStats((prev) => ({ ...prev, activeVisitors: j.data.activeVisitors }));
          }
        })
        .catch(() => {});
    }, 15000);

    return () => clearInterval(timer);
  }, [user]);

  return (
    <div className="admin-page">
      <PageHeader
        eyebrow="BẢNG ĐIỀU KHIỂN"
        title="Tổng quan hệ thống"
        description="Số liệu vận hành thực tế được tổng hợp từ các giao diện lập trình nội bộ."
        action={
          <button type="button" className="secondary" onClick={loadMetrics}>
            ↻ Làm mới dữ liệu
          </button>
        }
      />

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {loading ? (
        <Spinner label="Đang tải dữ liệu tổng quan…" />
      ) : (
        <>
          <div className="overview-grid" style={{ marginTop: '20px' }}>
            {/* Live Visitors Real-time card */}
            <div className="stat-card" style={{ borderLeft: '4px solid #10b981' }}>
              <div className="stat-icon" style={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#059669" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle cx="9" cy="7" r="4"></circle>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                </svg>
                <span
                  style={{
                    position: 'absolute',
                    top: -2,
                    right: -2,
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    backgroundColor: '#10b981',
                    border: '2px solid #ffffff',
                    boxShadow: '0 0 6px #10b981',
                  }}
                />
              </div>
              <div className="stat-content">
                <div className="stat-number" style={{ color: '#059669' }}>
                  {stats.activeVisitors}
                </div>
                <div className="stat-label">
                  Khách đang trực tuyến <span style={{ fontSize: '11px', color: '#059669' }}>(Thời gian thực)</span>
                </div>
              </div>
            </div>

            <div className="stat-card">
              <div className="stat-icon" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#0284c7" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                  <circle cx="12" cy="10" r="3"></circle>
                </svg>
              </div>
              <div className="stat-content">
                <div className="stat-number">{stats.totalPois}</div>
                <div className="stat-label">Điểm tham quan</div>
              </div>
            </div>

            <div className="stat-card">
              <div className="stat-icon" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#0284c7" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="16" y1="13" x2="8" y2="13"></line>
                  <line x1="16" y1="17" x2="8" y2="17"></line>
                </svg>
              </div>
              <div className="stat-content">
                <div className="stat-number">{stats.totalContents}</div>
                <div className="stat-label">Nội dung thuyết minh</div>
              </div>
            </div>

            {user?.role === 'SUPER_ADMIN' && stats.totalUsers !== null && (
              <>
                <div className="stat-card">
                  <div className="stat-icon" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#0284c7" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                      <circle cx="12" cy="7" r="4"></circle>
                    </svg>
                  </div>
                  <div className="stat-content">
                    <div className="stat-number">{stats.totalUsers}</div>
                    <div className="stat-label">Tài khoản quản trị</div>
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-icon" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={stats.lockedUsers ? '#dc2626' : '#64748b'} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                    </svg>
                  </div>
                  <div className="stat-content">
                    <div className="stat-number" style={{ color: stats.lockedUsers ? '#dc2626' : undefined }}>
                      {stats.lockedUsers}
                    </div>
                    <div className="stat-label">Tài khoản bị khóa tạm thời</div>
                  </div>
                </div>

                {stats.disabledUsers !== null && stats.disabledUsers > 0 && (
                  <div className="stat-card">
                    <div className="stat-icon">⛔</div>
                    <div className="stat-content">
                      <div className="stat-number" style={{ color: '#c53030' }}>
                        {stats.disabledUsers}
                      </div>
                      <div className="stat-label">Tài khoản bị vô hiệu hóa</div>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>

          <div className="panel" style={{ marginTop: '24px' }}>
            <div className="panel-heading">
              <div>
                <div className="eyebrow">ĐIỀU HƯỚNG NHANH</div>
                <h2>Chức năng quản trị</h2>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '16px' }}>
              <Link to="/pois" className="quick-link-card">
                <strong>Điểm tham quan (Bản đồ số)</strong>
                <p>Nhấp trực tiếp trên bản đồ để chọn tọa độ, tạo điểm và khởi tạo tác vụ thuyết minh.</p>
              </Link>

              <Link to="/contents" className="quick-link-card">
                <strong>Nội dung thuyết minh</strong>
                <p>Quản lý văn bản gốc, kiểm soát phiên bản và liên kết với điểm tham quan.</p>
              </Link>

              <Link to="/jobs/new" className="quick-link-card">
                <strong>Tạo tác vụ thuyết minh</strong>
                <p>Khởi chạy quy trình dịch thuật tự động và tổng hợp giọng nói đa ngôn ngữ.</p>
              </Link>

              <Link to="/jobs" className="quick-link-card">
                <strong>Lịch sử tác vụ gần đây</strong>
                <p>Theo dõi trạng thái và tiến độ xử lý của các tác vụ đã khởi tạo trong phiên.</p>
              </Link>

              {user?.role === 'SUPER_ADMIN' && (
                <Link to="/accounts" className="quick-link-card">
                  <strong>Quản trị tài khoản</strong>
                  <p>Tạo tài khoản mới, phân quyền, mở khóa và quản lý bảo mật.</p>
                </Link>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
};
