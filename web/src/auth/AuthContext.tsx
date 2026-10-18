import React, { createContext, useContext, useEffect, useState } from 'react';
import { authApi } from '../api/authApi';
import {
  AuthSession,
  clearSession,
  getSession,
  onAuthError,
  setSession,
} from '../api/apiClient';
import { UserSummary } from '../api/types';

interface AuthContextValue {
  session: AuthSession | null;
  user: UserSummary | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [session, setSessionState] = useState<AuthSession | null>(() => getSession());

  useEffect(() => {
    // Listen to 401 unrecoverable errors from apiClient
    const unsubscribe = onAuthError(() => {
      setSessionState(null);
    });
    return unsubscribe;
  }, []);

  const login = async (username: string, password: string) => {
    const data = await authApi.login({ username, password });
    const newSession: AuthSession = {
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      user: data.user,
    };
    setSession(newSession);
    setSessionState(newSession);
  };

  const logout = async () => {
    const current = session || getSession();
    if (current?.refreshToken) {
      try {
        await authApi.logout({ refreshToken: current.refreshToken });
      } catch {
        // Backend failure still clears client session
      }
    }
    clearSession();
    setSessionState(null);
  };

  const value: AuthContextValue = {
    session,
    user: session?.user || null,
    isAuthenticated: !!session?.accessToken,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
