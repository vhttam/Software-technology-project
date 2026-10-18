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
