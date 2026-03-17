// Keycloak REST API helpers for JWT-based auth.
// All requests go through Nginx → /auth/ → Keycloak.

const KEYCLOAK_URL = '/auth';
const REALM = 'night-watcher';
const CLIENT_ID = 'nw-dashboard';

const TOKEN_URL = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
const USERINFO_URL = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/userinfo`;
const LOGOUT_URL = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/logout`;
const ACCOUNT_URL = `${KEYCLOAK_URL}/realms/${REALM}/account`;
const ADMIN_URL = `${KEYCLOAK_URL}/admin/realms/${REALM}`;

// Login with username/password, returns tokens.
export async function login(username, password) {
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: CLIENT_ID,
    username,
    password,
  });

  const resp = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });

  if (!resp.ok) {
    const err = await resp.json().catch(() => ({}));
    throw new Error(err.error_description || 'Login failed');
  }

  return resp.json();
}

// Refresh access token using refresh_token.
export async function refreshToken(refreshToken) {
  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: CLIENT_ID,
    refresh_token: refreshToken,
  });

  const resp = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });

  if (!resp.ok) throw new Error('Token refresh failed');
  return resp.json();
}

// Logout (invalidate refresh token server-side).
export async function logout(refreshTokenValue) {
  const body = new URLSearchParams({
    client_id: CLIENT_ID,
    refresh_token: refreshTokenValue,
  });

  await fetch(LOGOUT_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  }).catch(() => {});
}

// Get current user info from token.
export async function getUserInfo(accessToken) {
  const resp = await fetch(USERINFO_URL, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!resp.ok) throw new Error('Failed to get user info');
  return resp.json();
}

// Parse JWT payload without verification (for reading claims client-side).
export function parseJwt(token) {
  try {
    const payload = token.split('.')[1];
    return JSON.parse(atob(payload));
  } catch {
    return null;
  }
}

// Check if token is expired (with 30s buffer).
export function isTokenExpired(token) {
  const claims = parseJwt(token);
  if (!claims || !claims.exp) return true;
  return Date.now() >= (claims.exp - 30) * 1000;
}

// Get user roles from JWT.
export function getTokenRoles(token) {
  const claims = parseJwt(token);
  if (!claims) return [];
  // Keycloak puts realm roles in realm_access.roles
  return claims.realm_access?.roles || [];
}

// --- Admin API (requires admin role) ---

function adminHeaders(accessToken) {
  return {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
  };
}

// List users (with optional search).
export async function listUsers(accessToken, search = '', first = 0, max = 20) {
  const params = new URLSearchParams({ first, max });
  if (search) params.set('search', search);

  const resp = await fetch(`${ADMIN_URL}/users?${params}`, {
    headers: adminHeaders(accessToken),
  });
  if (!resp.ok) throw new Error('Failed to list users');
  return resp.json();
}

// Get user count.
export async function getUserCount(accessToken) {
  const resp = await fetch(`${ADMIN_URL}/users/count`, {
    headers: adminHeaders(accessToken),
  });
  if (!resp.ok) throw new Error('Failed to get user count');
  return resp.json();
}

// Get single user.
export async function getUser(accessToken, userId) {
  const resp = await fetch(`${ADMIN_URL}/users/${userId}`, {
    headers: adminHeaders(accessToken),
  });
  if (!resp.ok) throw new Error('Failed to get user');
  return resp.json();
}

// Create user.
export async function createUser(accessToken, userData) {
  const resp = await fetch(`${ADMIN_URL}/users`, {
    method: 'POST',
    headers: adminHeaders(accessToken),
    body: JSON.stringify(userData),
  });
  if (!resp.ok) {
    const err = await resp.json().catch(() => ({}));
    throw new Error(err.errorMessage || 'Failed to create user');
  }
}

// Update user.
export async function updateUser(accessToken, userId, userData) {
  const resp = await fetch(`${ADMIN_URL}/users/${userId}`, {
    method: 'PUT',
    headers: adminHeaders(accessToken),
    body: JSON.stringify(userData),
  });
  if (!resp.ok) throw new Error('Failed to update user');
}

// Delete user.
export async function deleteUser(accessToken, userId) {
  const resp = await fetch(`${ADMIN_URL}/users/${userId}`, {
    method: 'DELETE',
    headers: adminHeaders(accessToken),
  });
  if (!resp.ok) throw new Error('Failed to delete user');
}

// Reset user password (admin).
export async function resetUserPassword(accessToken, userId, newPassword, temporary = false) {
  const resp = await fetch(`${ADMIN_URL}/users/${userId}/reset-password`, {
    method: 'PUT',
    headers: adminHeaders(accessToken),
    body: JSON.stringify({ type: 'password', value: newPassword, temporary }),
  });
  if (!resp.ok) throw new Error('Failed to reset password');
}

// Get realm roles.
export async function listRoles(accessToken) {
  const resp = await fetch(`${ADMIN_URL}/roles`, {
    headers: adminHeaders(accessToken),
  });
  if (!resp.ok) throw new Error('Failed to list roles');
  return resp.json();
}

// Get user's realm role mappings.
export async function getUserRoles(accessToken, userId) {
  const resp = await fetch(`${ADMIN_URL}/users/${userId}/role-mappings/realm`, {
    headers: adminHeaders(accessToken),
  });
  if (!resp.ok) throw new Error('Failed to get user roles');
  return resp.json();
}

// Assign realm roles to user.
export async function assignUserRoles(accessToken, userId, roles) {
  const resp = await fetch(`${ADMIN_URL}/users/${userId}/role-mappings/realm`, {
    method: 'POST',
    headers: adminHeaders(accessToken),
    body: JSON.stringify(roles),
  });
  if (!resp.ok) throw new Error('Failed to assign roles');
}
