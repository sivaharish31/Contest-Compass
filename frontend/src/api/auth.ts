import { api } from './client';
import type { UserProfile } from '../types/contest';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  username: string;
  password: string;
}

export function login(data: LoginRequest): Promise<UserProfile> {
  return api.post<UserProfile>('/auth/login', data);
}

export function register(data: RegisterRequest): Promise<UserProfile> {
  return api.post<UserProfile>('/auth/register', data);
}

export function logout(): Promise<void> {
  return api.post<void>('/auth/logout');
}

export function fetchCurrentUser(): Promise<UserProfile> {
  return api.get<UserProfile>('/auth/me');
}

export async function uploadAvatar(file: File): Promise<UserProfile> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch('/api/users/avatar', {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(body || res.statusText);
  }
  return res.json();
}

export async function deleteAvatar(): Promise<UserProfile> {
  const res = await fetch('/api/users/avatar', {
    method: 'DELETE',
    credentials: 'include',
  });
  if (!res.ok) throw new Error(res.statusText);
  return res.json();
}

export function updateUserProfile(data: {
  username?: string;
  timezone?: string;
  experienceLevel?: string;
}): Promise<UserProfile> {
  return api.put<UserProfile>('/auth/profile', data);
}
