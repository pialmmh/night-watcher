import { test, expect } from '@playwright/test';

// Dev mode bypasses auth — all pages accessible directly.

test.describe('Navigation & Page Rendering', () => {
  const pages = [
    { path: '/', title: 'Security Overview', selector: 'text=Security Overview' },
    { path: '/modules', title: 'Modules', selector: 'text=Modules' },
    { path: '/logs', title: 'Log Explorer', selector: 'text=Log Explorer' },
    { path: '/security', title: 'Security Events', selector: 'text=Security Events' },
    { path: '/waf', title: 'WAF', selector: 'text=WAF' },
    { path: '/watchdog', title: 'Watchdog', selector: 'text=Watchdog' },
    { path: '/network', title: 'Network', selector: 'text=Network' },
    { path: '/ha', title: 'HA Cluster', selector: 'text=HA Cluster' },
    { path: '/profile', title: 'Profile', selector: 'text=Profile' },
    { path: '/gateway', title: 'API Gateway', selector: 'text=API Gateway' },
    { path: '/gateway/policies', title: 'Gateway Policies', selector: 'text=Gateway Policies' },
    { path: '/gateway/audit', title: 'Audit Logs', selector: 'text=Audit Logs' },
    { path: '/gateway/keycloak', title: 'Keycloak Identity', selector: 'text=Keycloak Identity' },
    { path: '/users', title: 'User Management', selector: 'text=User Management' },
    { path: '/roles', title: 'Role Management', selector: 'text=Role Management' },
    { path: '/sessions', title: 'Active Sessions', selector: 'text=Active Sessions' },
    { path: '/events', title: 'Events', selector: 'text=Events' },
    { path: '/realm', title: 'Realm Settings', selector: 'text=Realm Settings' },
    { path: '/clients', title: 'Client Management', selector: 'text=Client Management' },
  ];

  for (const p of pages) {
    test(`${p.title} page renders at ${p.path}`, async ({ page }) => {
      await page.goto(p.path);
      await page.waitForTimeout(2000);
      const heading = page.locator(p.selector).first();
      await expect(heading).toBeVisible({ timeout: 8000 });
    });
  }
});

test.describe('Login Page', () => {
  test('renders login form', async ({ page }) => {
    await page.goto('/login');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Night-Watcher')).toBeVisible();
    await expect(page.locator('text=Sign in to continue')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible();
  });
});

test.describe('Navigation Drawer', () => {
  test('shows all nav sections', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);
    // Security section
    await expect(page.locator('text=Overview').first()).toBeVisible();
    await expect(page.locator('text=HA Cluster').first()).toBeVisible();
    // Access Control section
    await expect(page.locator('text=Access Control').first()).toBeVisible();
    await expect(page.locator('text=API Gateway').first()).toBeVisible();
    // Identity section (admin only)
    await expect(page.locator('text=Identity').first()).toBeVisible();
    await expect(page.locator('text=Roles').first()).toBeVisible();
    // Account section
    await expect(page.locator('text=Logout').first()).toBeVisible();
  });

  test('nav links work', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);
    // Click API Gateway
    await page.locator('text=API Gateway').first().click();
    await page.waitForTimeout(1000);
    await expect(page).toHaveURL(/\/gateway/);
    await expect(page.locator('text=Authorization Policies')).toBeVisible();
  });
});

test.describe('Gateway Overview', () => {
  test('shows policy cards and request flow', async ({ page }) => {
    await page.goto('/gateway');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Auth Policies')).toBeVisible();
    await expect(page.locator('text=Public Endpoints')).toBeVisible();
    await expect(page.locator('text=Super Admin')).toBeVisible();
    await expect(page.locator('text=Portal User')).toBeVisible();
    await expect(page.locator('text=Request Flow')).toBeVisible();
    await expect(page.locator('text=Backend Services')).toBeVisible();
  });
});

test.describe('Gateway Policies', () => {
  test('shows endpoint policies tab with accordions', async ({ page }) => {
    await page.goto('/gateway/policies');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Endpoint Policies')).toBeVisible();
    await expect(page.locator('text=Data Access Rules')).toBeVisible();
    // CallingPortalUser accordion should be expanded by default
    await expect(page.locator('text=Portal User')).toBeVisible();
    await expect(page.locator('text=315+ endpoints')).toBeVisible();
  });

  test('shows data access rules tab', async ({ page }) => {
    await page.goto('/gateway/policies');
    await page.waitForTimeout(2000);
    await page.locator('text=Data Access Rules').click();
    await page.waitForTimeout(1000);
    await expect(page.locator('text=Endpoint Pattern')).toBeVisible();
    await expect(page.locator('text=Payload Field')).toBeVisible();
    await expect(page.locator('text=/get-partner').first()).toBeVisible();
  });
});

test.describe('Gateway Audit', () => {
  test('shows audit log table with search', async ({ page }) => {
    await page.goto('/gateway/audit');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Audit Logs')).toBeVisible();
    await expect(page.locator('input[placeholder*="Search"]')).toBeVisible();
    // Demo data should show in table
    await expect(page.locator('td:has-text("admin@telcobright.com")').first()).toBeVisible();
  });

  test('search filters audit logs', async ({ page }) => {
    await page.goto('/gateway/audit');
    await page.waitForTimeout(2000);
    await page.fill('input[placeholder*="Search"]', 'reseller');
    await page.waitForTimeout(500);
    await expect(page.locator('text=reseller@cosmocom.net').first()).toBeVisible();
    // admin should be filtered out
    await expect(page.locator('tr:has-text("admin@telcobright.com")')).toHaveCount(0);
  });
});

test.describe('Keycloak Admin Page', () => {
  test('shows realm config tabs', async ({ page }) => {
    await page.goto('/gateway/keycloak');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Keycloak Identity')).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Realm Config' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Clients' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Integration' })).toBeVisible();
  });

  test('shows integration tab with config snippets', async ({ page }) => {
    await page.goto('/gateway/keycloak');
    await page.waitForTimeout(2000);
    await page.getByRole('tab', { name: 'Integration' }).click();
    await page.waitForTimeout(1000);
    await expect(page.locator('text=API Gateway Integration')).toBeVisible();
    await expect(page.locator('text=Nginx JWT Validation')).toBeVisible();
  });
});

test.describe('User Management', () => {
  test('shows user table with action buttons', async ({ page }) => {
    await page.goto('/users');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=User Management')).toBeVisible();
    await expect(page.locator('text=Create User')).toBeVisible();
    await expect(page.locator('input[placeholder*="Search"]')).toBeVisible();
    // Table headers
    await expect(page.locator('th:has-text("Username")')).toBeVisible();
    await expect(page.locator('th:has-text("Email")')).toBeVisible();
  });

  test('create user dialog opens with validation', async ({ page }) => {
    await page.goto('/users');
    await page.waitForTimeout(2000);
    await page.getByRole('button', { name: 'Create User' }).click();
    await page.waitForTimeout(500);
    await expect(page.locator('text=Create User').last()).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    // Try submitting empty — should show validation error
    await page.getByRole('button', { name: 'Save' }).click();
    await page.waitForTimeout(500);
    await expect(page.locator('.MuiAlert-root')).toBeVisible();
  });
});

test.describe('Role Management', () => {
  test('shows roles table with create button', async ({ page }) => {
    await page.goto('/roles');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Role Management')).toBeVisible();
    await expect(page.locator('text=Create Role')).toBeVisible();
    await expect(page.locator('th:has-text("Role Name")')).toBeVisible();
  });

  test('create role dialog validates name', async ({ page }) => {
    await page.goto('/roles');
    await page.waitForTimeout(2000);
    await page.getByRole('button', { name: 'Create Role' }).click();
    await page.waitForTimeout(500);
    // Try saving empty
    await page.getByRole('button', { name: 'Save' }).click();
    await page.waitForTimeout(500);
    await expect(page.locator('.MuiAlert-root')).toBeVisible();
  });
});

test.describe('Session Management', () => {
  test('renders session page with refresh', async ({ page }) => {
    await page.goto('/sessions');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Active Sessions')).toBeVisible();
    await expect(page.locator('text=Refresh')).toBeVisible();
  });
});

test.describe('Events', () => {
  test('renders events page with tabs', async ({ page }) => {
    await page.goto('/events');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Events')).toBeVisible();
    await expect(page.locator('text=Login Events')).toBeVisible();
    await expect(page.locator('text=Admin Events')).toBeVisible();
  });
});

test.describe('Realm Settings', () => {
  test('renders realm settings with save button', async ({ page }) => {
    await page.goto('/realm');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Realm Settings')).toBeVisible();
    await expect(page.locator('text=Save Changes')).toBeVisible();
    await expect(page.locator('text=General')).toBeVisible();
    await expect(page.locator('text=Token Lifespans')).toBeVisible();
    await expect(page.locator('text=Brute Force Protection')).toBeVisible();
    await expect(page.locator('text=Password Policy')).toBeVisible();
  });
});

test.describe('Client Management', () => {
  test('shows client table with create button', async ({ page }) => {
    await page.goto('/clients');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Client Management')).toBeVisible();
    await expect(page.locator('text=Create Client')).toBeVisible();
  });

  test('create client dialog validates client ID', async ({ page }) => {
    await page.goto('/clients');
    await page.waitForTimeout(2000);
    await page.getByRole('button', { name: 'Create Client' }).click();
    await page.waitForTimeout(500);
    // Try saving empty
    await page.getByRole('button', { name: 'Save' }).click();
    await page.waitForTimeout(500);
    await expect(page.locator('.MuiAlert-root')).toBeVisible();
  });
});

test.describe('Profile', () => {
  test('shows profile info and password form', async ({ page }) => {
    await page.goto('/profile');
    await page.waitForTimeout(2000);
    await expect(page.locator('text=Profile')).toBeVisible();
    await expect(page.locator('text=Account Info')).toBeVisible();
    await expect(page.locator('text=Change Password')).toBeVisible();
    await expect(page.locator('text=dev-admin')).toBeVisible();
  });

  test('password change validates policy', async ({ page }) => {
    await page.goto('/profile');
    await page.waitForTimeout(2000);
    // Fill weak password
    await page.fill('input[type="password"] >> nth=0', 'current');
    await page.fill('input[type="password"] >> nth=1', 'short');
    await page.fill('input[type="password"] >> nth=2', 'short');
    await page.getByRole('button', { name: 'Change Password' }).click();
    await page.waitForTimeout(500);
    await expect(page.locator('.MuiAlert-root')).toBeVisible();
  });
});
