import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { Layout } from './components/Layout';
import { AccountsPage } from './pages/AccountsPage';
import { ContentsPage } from './pages/ContentsPage';
import { CreateJobPage } from './pages/CreateJobPage';
import { JobDetailPage } from './pages/JobDetailPage';
import { JobHistoryPage } from './pages/JobHistoryPage';
import { LoginPage } from './pages/LoginPage';
import { OverviewPage } from './pages/OverviewPage';
import { PoisPage } from './pages/PoisPage';

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      {/* Public Login Route without main layout */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/admin/login" element={<LoginPage />} />

      {/* Admin Layout wrapping all authenticated routes */}
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route index element={<OverviewPage />} />
        <Route path="admin" element={<OverviewPage />} />
        <Route path="admin/dashboard" element={<OverviewPage />} />
        <Route path="admin/overview" element={<OverviewPage />} />
        <Route path="admin/pois" element={<PoisPage />} />
        <Route path="admin/contents" element={<ContentsPage />} />
        <Route path="admin/jobs/new" element={<CreateJobPage />} />
        <Route path="admin/jobs/:id" element={<JobDetailPage />} />
        <Route path="admin/jobs" element={<JobHistoryPage />} />
        <Route
          path="admin/accounts"
          element={
            <ProtectedRoute requiredRole="SUPER_ADMIN">
              <AccountsPage />
            </ProtectedRoute>
          }
        />
        <Route path="admin.html" element={<OverviewPage />} />
        <Route path="dashboard" element={<OverviewPage />} />
        <Route path="overview" element={<OverviewPage />} />
        <Route path="pois" element={<PoisPage />} />
        <Route path="contents" element={<ContentsPage />} />
        <Route path="jobs/new" element={<CreateJobPage />} />
        <Route path="jobs/:id" element={<JobDetailPage />} />
        <Route path="jobs" element={<JobHistoryPage />} />
        <Route
          path="accounts"
          element={
            <ProtectedRoute requiredRole="SUPER_ADMIN">
              <AccountsPage />
            </ProtectedRoute>
          }
        />

        {/* Fallback to Overview */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
};

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}
