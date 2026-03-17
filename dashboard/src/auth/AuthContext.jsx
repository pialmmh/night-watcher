import { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react';
import {
  login as kcLogin,
  logout as kcLogout,
  refreshToken as kcRefresh,
  parseJwt,
  isTokenExpired,
  getTokenRoles,
} from './keycloak';

const AuthContext = createContext(null);

// Dev mode: bypass auth when Keycloak is not available.
// Set to false in production.
const DEV_BYPASS_AUTH = import.meta.env.DEV && !import.meta.env.VITE_KEYCLOAK_ENABLED;

export function AuthProvider({ children }) {
  const [accessToken, setAccessToken] = useState(() => DEV_BYPASS_AUTH ? 'dev-token' : sessionStorage.getItem('access_token'));
  const [refreshTokenVal, setRefreshToken] = useState(() => DEV_BYPASS_AUTH ? 'dev-refresh' : sessionStorage.getItem('refresh_token'));
  const [user, setUser] = useState(() => {
    if (DEV_BYPASS_AUTH) return { preferred_username: 'dev-admin', name: 'Dev Admin', email: 'admin@dev', realm_access: { roles: ['admin'] } };
    const t = sessionStorage.getItem('access_token');
    return t ? parseJwt(t) : null;
  });
  const refreshTimer = useRef(null);

  const saveTokens = useCallback((tokens) => {
    setAccessToken(tokens.access_token);
    setRefreshToken(tokens.refresh_token);
    setUser(parseJwt(tokens.access_token));
    sessionStorage.setItem('access_token', tokens.access_token);
    sessionStorage.setItem('refresh_token', tokens.refresh_token);
  }, []);

  const clearTokens = useCallback(() => {
    setAccessToken(null);
    setRefreshToken(null);
    setUser(null);
    sessionStorage.removeItem('access_token');
    sessionStorage.removeItem('refresh_token');
    if (refreshTimer.current) clearTimeout(refreshTimer.current);
  }, []);

  const login = useCallback(async (username, password) => {
    const tokens = await kcLogin(username, password);
    saveTokens(tokens);
    return tokens;
  }, [saveTokens]);

  const logout = useCallback(async () => {
    if (refreshTokenVal) {
      await kcLogout(refreshTokenVal);
    }
    clearTokens();
  }, [refreshTokenVal, clearTokens]);

  // Get a valid access token, refreshing if needed.
  const getToken = useCallback(async () => {
    if (accessToken && !isTokenExpired(accessToken)) {
      return accessToken;
    }
    if (!refreshTokenVal) {
      clearTokens();
      return null;
    }
    try {
      const tokens = await kcRefresh(refreshTokenVal);
      saveTokens(tokens);
      return tokens.access_token;
    } catch {
      clearTokens();
      return null;
    }
  }, [accessToken, refreshTokenVal, saveTokens, clearTokens]);

  // Schedule token refresh before expiry.
  useEffect(() => {
    if (!accessToken) return;
    const claims = parseJwt(accessToken);
    if (!claims?.exp) return;

    const expiresIn = claims.exp * 1000 - Date.now();
    // Refresh 60s before expiry.
    const refreshIn = Math.max(expiresIn - 60000, 5000);

    refreshTimer.current = setTimeout(async () => {
      try {
        const tokens = await kcRefresh(refreshTokenVal);
        saveTokens(tokens);
      } catch {
        clearTokens();
      }
    }, refreshIn);

    return () => {
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
    };
  }, [accessToken, refreshTokenVal, saveTokens, clearTokens]);

  const isAuthenticated = DEV_BYPASS_AUTH || (!!accessToken && !isTokenExpired(accessToken));
  const roles = DEV_BYPASS_AUTH ? ['admin'] : (accessToken ? getTokenRoles(accessToken) : []);
  const isAdmin = roles.includes('admin');

  return (
    <AuthContext.Provider value={{
      user,
      accessToken,
      isAuthenticated,
      roles,
      isAdmin,
      login,
      logout,
      getToken,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
