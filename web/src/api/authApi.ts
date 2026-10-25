import { apiClient } from './apiClient';
import {
  ChangeOwnPasswordRequest,
  ChangePasswordRequest,
  CreateUserRequest,
  ListUsersResponseData,
  LoginRequest,
  LoginResponseData,
  LogoutRequest,
  RefreshRequest,
  RefreshResponseData,
  ResetPasswordRequest,
  UnlockUserResponseData,
  UserAccountItem,
} from './types';

export const authApi = {
  login(body: LoginRequest): Promise<LoginResponseData> {
    return apiClient<LoginResponseData>('/api/v1/auth/login', {
      method: 'POST',
      body,
      auth: false,
    });
  },

  refresh(body: RefreshRequest): Promise<RefreshResponseData> {
    return apiClient<RefreshResponseData>('/api/v1/auth/refresh', {
      method: 'POST',
      body,
      auth: false,
    });
  },

  logout(body: LogoutRequest): Promise<void> {
    return apiClient<void>('/api/v1/auth/logout', {
      method: 'POST',
      body,
    });
  },

  listUsers(page = 1, pageSize = 20, query?: string, role?: string, status?: string, locked?: boolean): Promise<ListUsersResponseData> {
    const params = new URLSearchParams({
      page: String(page),
      pageSize: String(pageSize),
    });
    if (query?.trim()) params.set('query', query.trim());
    if (role?.trim()) params.set('role', role.trim());
    if (status?.trim()) params.set('status', status.trim());
    if (locked !== undefined) params.set('locked', String(locked));

    return apiClient<ListUsersResponseData>(`/api/v1/auth/users?${params.toString()}`);
  },

  createUser(body: CreateUserRequest): Promise<UserAccountItem> {
    return apiClient<UserAccountItem>('/api/v1/auth/users', {
      method: 'POST',
      body,
    });
  },

  changePassword(userId: string, body: ChangePasswordRequest): Promise<void> {
    return apiClient<void>(`/api/v1/auth/users/${encodeURIComponent(userId)}/password`, {
      method: 'PUT',
      body,
    });
  },

  changeOwnPassword(body: ChangeOwnPasswordRequest): Promise<void> {
    return apiClient<void>('/api/v1/auth/password', {
      method: 'PUT',
      body,
    });
  },

  resetSubordinatePassword(userId: string, body: ResetPasswordRequest): Promise<void> {
    return apiClient<void>(`/api/v1/auth/users/${encodeURIComponent(userId)}/reset-password`, {
      method: 'PUT',
      body,
    });
  },

  disableUser(userId: string): Promise<void> {
    return apiClient<void>(`/api/v1/auth/users/${encodeURIComponent(userId)}/disable`, {
      method: 'PUT',
    });
  },

  enableUser(userId: string): Promise<void> {
    return apiClient<void>(`/api/v1/auth/users/${encodeURIComponent(userId)}/enable`, {
      method: 'PUT',
    });
  },

  unlockUser(userId: string): Promise<UnlockUserResponseData> {
    return apiClient<UnlockUserResponseData>(`/api/v1/auth/users/${encodeURIComponent(userId)}/unlock`, {
      method: 'PUT',
    });
  },
};

