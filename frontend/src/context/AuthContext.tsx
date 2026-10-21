import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react';
import type { UserProfile } from '../types/contest';
import { fetchCurrentUser, login as apiLogin, logout as apiLogout, register as apiRegister } from '../api/auth';
import type { LoginRequest, RegisterRequest } from '../api/auth';
import { ApiError } from '../api/client';

interface AuthState {
  user: UserProfile | null;
  loading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchCurrentUser()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (data: LoginRequest) => {
    const u = await apiLogin(data);
    setUser(u);
  }, []);

  const register = useCallback(async (data: RegisterRequest) => {
    const u = await apiRegister(data);
    setUser(u);
  }, []);

  const logout = useCallback(async () => {
    // Always clear local auth state, regardless of server response.
    // A logout must never leave the UI in an authenticated state.
    setUser(null);
    try {
      await apiLogout();
    } catch (e) {
      // 401 means the session was already gone — silently accepted.
      // Any other error is logged but does not prevent the local logout.
      if (e instanceof ApiError && e.status !== 401) {
        console.error('Logout request failed:', e.status);
      }
    }
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const u = await fetchCurrentUser();
      setUser(u);
    } catch {
      // session may have expired
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
