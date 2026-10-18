import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

interface ProtectedRouteProps {
  requiredRole?: string;
  children?: React.ReactNode;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ requiredRole, children }) => {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (requiredRole && user?.role !== requiredRole) {
    return (
      <div className="empty" style={{ margin: '40px auto', maxWidth: '600px' }}>
        <div style={{ color: '#a44d3e', fontWeight: 'bold', marginBottom: '8px' }}>
          FORBIDDEN (403)
        </div>
        <div>Tài khoản của bạn ({user?.role || 'Chưa xác định'}) không có quyền truy cập trang này.</div>
      </div>
    );
  }

  return children ? <>{children}</> : null;
};
