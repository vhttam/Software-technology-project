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
}

export const OverviewPage: React.FC = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState<OverviewStats>({
    totalPois: 0,
    totalContents: 0,
    totalUsers: null,
    lockedUsers: null,
    disabledUsers: null,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const loadMetrics = async () => {
    setLoading(true);
    setError(null);
    try {
      const [poisRes, contentsRes] = await Promise.all([
        contentApi.listPois(1, 1).catch(() => ({ totalItems: 0 })),
        contentApi.listContents(1, 1).catch(() => ({ totalItems: 0 })),
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
      });
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMetrics();
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
            <div className="stat-card">
              <div className="stat-icon">🏛</div>
              <div className="stat-content">
                <div className="stat-number">{stats.totalPois}</div>
                <div className="stat-label">Điểm tham quan</div>
              </div>
            </div>

            <div className="stat-card">
              <div className="stat-icon">📄</div>
              <div className="stat-content">
                <div className="stat-number">{stats.totalContents}</div>
                <div className="stat-label">Nội dung thuyết minh</div>
              </div>
            </div>

            {user?.role === 'SUPER_ADMIN' && stats.totalUsers !== null && (
              <>
                <div className="stat-card">
                  <div className="stat-icon">👥</div>
                  <div className="stat-content">
                    <div className="stat-number">{stats.totalUsers}</div>
                    <div className="stat-label">Tài khoản quản trị</div>
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-icon">🔒</div>
                  <div className="stat-content">
                    <div className="stat-number" style={{ color: stats.lockedUsers ? '#c53030' : undefined }}>
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
                <strong>Điểm tham quan</strong>
                <p>Xem danh sách, quản lý mã QR, tạo mới và chỉnh sửa điểm di tích.</p>
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
