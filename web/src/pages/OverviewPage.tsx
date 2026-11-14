import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { contentApi } from '../api/contentApi';
import { authApi } from '../api/authApi';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/ErrorBanner';
import { Spinner } from '../components/Spinner';
import { PageHeader } from '../components/PageHeader';
import { ListeningAnalyticsResponseData } from '../api/types';

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
  const [listeningData, setListeningData] = useState<ListeningAnalyticsResponseData | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const loadMetrics = async () => {
    setLoading(true);
    setAnalyticsLoading(true);
    setError(null);
    try {
      const [poisRes, contentsRes, presenceRes, analyticsRes] = await Promise.all([
        contentApi.listPois(1, 1).catch(() => ({ totalItems: 0 })),
        contentApi.listContents(1, 1).catch(() => ({ totalItems: 0 })),
        fetch('/api/v1/client/presence/count')
          .then((r) => r.json())
          .then((j) => (j && j.data && typeof j.data.activeVisitors === 'number' ? j.data.activeVisitors : 0))
          .catch(() => 0),
        contentApi.getListeningAnalytics().catch((err) => {
          console.warn('Failed to load listening analytics:', err);
          return null;
        }),
      ]);

      setListeningData(analyticsRes);
      setAnalyticsLoading(false);

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

          {/* Biểu đồ phân tích trực quan (Visual Analytics Section) */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '20px', marginTop: '24px' }}>
            {/* 1. Biểu đồ tròn: Cơ cấu ngôn ngữ khách nghe */}
            <div className="panel" style={{ margin: 0, padding: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <div>
                  <div className="eyebrow" style={{ color: '#0284c7' }}>THỊ PHẦN NGÔN NGỮ</div>
                  <h3 style={{ margin: '4px 0 0 0', fontSize: '16px', color: '#0f172a' }}>Tỷ lệ ngôn ngữ du khách sử dụng</h3>
                </div>
                <span style={{ fontSize: '11px', background: '#e0f2fe', color: '#0369a1', padding: '3px 8px', borderRadius: '12px', fontWeight: 600 }}>Thống kê tháng</span>
              </div>

              {analyticsLoading ? (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '150px' }}>
                  <Spinner />
                </div>
              ) : !listeningData || !listeningData.languageShares || listeningData.languageShares.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px 10px', color: '#64748b', fontSize: '13px' }}>
                  Chưa có dữ liệu
                </div>
              ) : (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-around', gap: '16px', flexWrap: 'wrap' }}>
                  {/* SVG Donut Chart */}
                  <div style={{ position: 'relative', width: '150px', height: '150px' }}>
                    <svg width="150" height="150" viewBox="0 0 42 42">
                      <circle cx="21" cy="21" r="15.91549430918954" fill="transparent" stroke="#e2e8f0" strokeWidth="6"></circle>
                      {(() => {
                        const colors = ['#0284c7', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#6366f1'];
                        let accumulated = 0;
                        return listeningData.languageShares.map((item, idx) => {
                          const strokeDash = `${item.percentage} ${100 - item.percentage}`;
                          // SVG circle offset formula: 25 is 12 o'clock in SVG dashoffset
                          const strokeOffset = (100 - accumulated + 25) % 100;
                          accumulated += item.percentage;
                          return (
                            <circle
                              key={item.lang}
                              cx="21"
                              cy="21"
                              r="15.91549430918954"
                              fill="transparent"
                              stroke={colors[idx % colors.length]}
                              strokeWidth="6"
                              strokeDasharray={strokeDash}
                              strokeDashoffset={strokeOffset}
                            ></circle>
                          );
                        });
                      })()}
                    </svg>
                    <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', textAlign: 'center' }}>
                      <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#0f172a' }}>100%</div>
                      <div style={{ fontSize: '10px', color: '#64748b' }}>Đa ngôn ngữ</div>
                    </div>
                  </div>

                  {/* Chú thích Donut */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '12px' }}>
                    {(() => {
                      const colors = ['#0284c7', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#6366f1'];
                      const langNames: Record<string, string> = {
                        vi: 'Tiếng Việt',
                        en: 'Tiếng Anh',
                        fr: 'Tiếng Pháp',
                        ja: 'Tiếng Nhật',
                        ko: 'Tiếng Hàn',
                        zh: 'Tiếng Trung',
                      };
                      return listeningData.languageShares.map((item, idx) => {
                        const name = langNames[item.lang.toLowerCase()] || item.lang.toUpperCase();
                        return (
                          <div key={item.lang} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span style={{ width: '10px', height: '10px', borderRadius: '50%', backgroundColor: colors[idx % colors.length] }}></span>
                            <span style={{ color: '#334155', fontWeight: 500 }}>{name} ({item.lang}): <strong>{item.percentage}%</strong></span>
                          </div>
                        );
                      });
                    })()}
                  </div>
                </div>
              )}
            </div>

            {/* 2. Biểu đồ cột: Lưu lượng khách theo khung giờ */}
            <div className="panel" style={{ margin: 0, padding: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <div>
                  <div className="eyebrow" style={{ color: '#10b981' }}>PHÂN BỔ TRUY CẬP</div>
                  <h3 style={{ margin: '4px 0 0 0', fontSize: '16px', color: '#0f172a' }}>Lượng khách nghe theo khung giờ</h3>
                </div>
                <span style={{ fontSize: '11px', background: '#dcfce7', color: '#15803d', padding: '3px 8px', borderRadius: '12px', fontWeight: 600 }}>Hôm nay</span>
              </div>

              {analyticsLoading ? (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '130px' }}>
                  <Spinner />
                </div>
              ) : !listeningData || !listeningData.hourlyDistribution || listeningData.totalEvents === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px 10px', color: '#64748b', fontSize: '13px' }}>
                  Chưa có dữ liệu
                </div>
              ) : (
                <>
                  {/* Bar Chart Visualization */}
                  <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', height: '130px', padding: '10px 10px 0 10px', borderBottom: '1px solid #e2e8f0', gap: '8px' }}>
                    {(() => {
                      const allHours = listeningData.hourlyDistribution || [];
                      // Filter down to common working hours or hours with traffic (e.g. 08h, 10h, 12h, 14h, 16h, 18h)
                      const sampleHours = [8, 10, 12, 14, 16, 18];
                      const displayBars = sampleHours.map(h => {
                        const found = allHours.find(item => item.hour === h);
                        return found || { hour: h, label: `${h < 10 ? '0' : ''}${h}h`, count: 0, percentage: 0 };
                      });
                      const maxCount = Math.max(...displayBars.map(b => b.count), 1);
                      return displayBars.map((bar, idx) => {
                        const isHighlight = bar.count > 0 && bar.count === maxCount;
                        const heightPct = bar.count > 0 ? Math.max(15, Math.round((bar.count * 100) / maxCount)) : 5;
                        return (
                          <div key={idx} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flex: 1, height: '100%', justifyContent: 'flex-end' }}>
                            <span style={{ fontSize: '10px', color: isHighlight ? '#059669' : '#64748b', fontWeight: isHighlight ? 700 : 500, marginBottom: '4px' }}>
                              {bar.count}
                            </span>
                            <div
                              style={{
                                width: '100%',
                                maxWidth: '28px',
                                height: `${heightPct}%`,
                                backgroundColor: isHighlight ? '#10b981' : '#cbd5e1',
                                borderRadius: '4px 4px 0 0',
                                transition: 'height 0.3s ease',
                              }}
                            ></div>
                            <span style={{ fontSize: '11px', color: '#475569', marginTop: '6px', fontWeight: 500 }}>{bar.label}</span>
                          </div>
                        );
                      });
                    })()}
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', color: '#64748b', marginTop: '8px' }}>
                    <span>Giờ cao điểm: <strong>{listeningData.peakInterval || 'Chưa xác định'}</strong></span>
                    <span>Tỷ lệ hoàn thành bài nghe: <strong>{listeningData.completionRate != null ? `${listeningData.completionRate}%` : 'Chưa có dữ liệu'}</strong></span>
                  </div>
                </>
              )}
            </div>
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
