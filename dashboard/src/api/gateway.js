// API Gateway data — hardcoded from gateway source code.
// In future, these can be fetched from a gateway management API.

export const POLICY_TYPES = [
  { type: 'Allow', label: 'Super Admin', description: 'Full access to all endpoints', roles: ['ROLE_ADMIN', 'ROLE_SMSADMIN'], color: '#d32f2f' },
  { type: 'CallingPortalUser', label: 'Portal User', description: 'Default user with PBX, SMS, and partner access', roles: ['ROLE_USER'], color: '#1565c0' },
  { type: 'BtrcUser', label: 'BTRC User', description: 'Regulatory authority — QoS reports and live call monitoring', roles: ['ROLE_BTRC'], color: '#7b1fa2' },
  { type: 'ReadOnly', label: 'Read Only', description: 'Dashboard and report viewing only', roles: ['ROLE_READONLY'], color: '#616161' },
  { type: 'Reseller', label: 'Reseller', description: 'Partner management, packages, DID, and SMS operations', roles: ['ROLE_RESELLER'], color: '#2e7d32' },
  { type: 'WebRtc', label: 'WebRTC', description: 'WebRTC calling — contacts and call history', roles: ['ROLE_WEBRTC'], color: '#e65100' },
  { type: 'SmsSender', label: 'SMS Sender', description: 'SMS campaign send only', roles: ['ROLE_SMSSENDER'], color: '#00838f' },
  { type: 'Deny', label: 'Deny All', description: 'Explicitly denies all access', roles: [], color: '#424242' },
];

export const POLICIES = {
  Allow: { endpoints: ['* (all endpoints)'], count: 'unlimited' },
  CallingPortalUser: {
    count: '315+',
    categories: [
      { name: 'Authentication', endpoints: ['token/create-api-key', 'get-active-api-keys-for-user', 'delete-api-key', 'getUserByEmail', 'logout', 'getUser', 'auth/createUser', 'editUser', 'deleteUser', 'getUserByIdPartner', 'permissions/*'] },
      { name: 'PBX — Domains & Extensions', endpoints: ['domains/create', 'domains/get-by-uuid', 'extensions/create', 'extensions/list-by-domain', 'extensions/update', 'extensions/delete'] },
      { name: 'PBX — Gateways & Routes', endpoints: ['gateways/create', 'gateways/list-by-domain', 'outbound-routes/create', 'inbound-routes/create', 'routes/get', 'routes/create'] },
      { name: 'PBX — Ring Groups & Conferences', endpoints: ['ring-groups/create', 'ring-groups/list', 'ring-groups/toggle', 'conferences/create', 'conferences/list-by-domain'] },
      { name: 'PBX — Recordings & CDR', endpoints: ['recordings/create', 'recordings/list', 'recordings/download', 'cdr/stats', 'cdr/list-by-domain'] },
      { name: 'Call Center', endpoints: ['agents/list', 'agents/set-status', 'queues/list', 'queues/live', 'tiers/add', 'tiers/remove'] },
      { name: 'Call Broadcast', endpoints: ['broadcast/create', 'broadcast/start', 'broadcast/stop', 'broadcast/upload-leads'] },
      { name: 'Partner & Billing', endpoints: ['get-partner', 'update-partner', 'packages/get', 'packages/purchase', 'topup', 'DID operations'] },
      { name: 'SMS REST', endpoints: ['campaigns/*', 'templates/*', 'policies/*', 'contact-groups/*', 'forbidden-words/*', 'voice-broadcast/*'] },
      { name: 'Dashboard & Live', endpoints: ['balance', 'stats', 'ws/live-calls', 'concurrent-call-profile'] },
    ],
  },
  BtrcUser: {
    count: 11,
    categories: [
      { name: 'QoS & Monitoring', endpoints: ['/AUTHENTICATION/logout', '/FREESWITCHREST/ws/live-calls', '/FREESWITCHREST/qos-reports/call-drop', '/FREESWITCHREST/qos-reports/cssr', '/FREESWITCHREST/mcc-reports/export/csv', '/FREESWITCHREST/mcc-reports/max-call-periodically', '/FREESWITCHREST/mcc-reports/export/excel', '/FREESWITCHREST/get-partner-details', '/FREESWITCHREST/get-outgoing-calls', '/FREESWITCHREST/get-btcl-calls', '/FREESWITCHREST/concurrent-call-profile', '/FREESWITCHREST/admin-live-calls-summary'] },
    ],
  },
  ReadOnly: {
    count: 24,
    categories: [
      { name: 'Dashboard & Reports', endpoints: ['/AUTHENTICATION/logout', '/AUTHENTICATION/getUserByEmail', '/FREESWITCHREST/ws/live-calls', '/FREESWITCHREST/partner/get-partner', '/FREESWITCHREST/partner/get-partners', '/FREESWITCHREST/admin/DashBoard/system-info', '/FREESWITCHREST/admin/DashBoard/top5PartnerCallCounts', '/FREESWITCHREST/admin/DashBoard/get-partner-balances', '/FREESWITCHREST/admin/DashBoard/getTotalCall', '/FREESWITCHREST/admin/DashBoard/getOutgoingCall', '/FREESWITCHREST/admin/DashBoard/getIncomingCall', '/FREESWITCHREST/admin/DashBoard/getMissedCall', '/FREESWITCHREST/admin/DashBoard/getIntervalWiseCall', '/FREESWITCHREST/concurrent-call-partner-wise', '/FREESWITCHREST/concurrent-call-profile', '/FREESWITCHREST/get-callsrcs', '/FREESWITCHREST/get-retail-partners', '/FREESWITCHREST/get-audit-logs', '/FREESWITCHREST/api/recordings/get-recordings', '/FREESWITCHREST/api/recordings/auto-load', '/FREESWITCHREST/api/sofia/registrations', '/FREESWITCHREST/package/get-all-purchase', '/FREESWITCHREST/package/update-onselect-Priority', '/FREESWITCHREST/admin-live-calls-summary'] },
    ],
  },
  Reseller: {
    count: '90+',
    categories: [
      { name: 'User & Partner Management', endpoints: ['logout', 'getUserByEmail', 'getUsers', 'getRoles', 'getUser', 'editUser', 'deleteUser', 'get-partner', 'get-partners', 'create-partner', 'update-partner'] },
      { name: 'Packages & Billing', endpoints: ['get-packages', 'create-package', 'purchase-package', 'topup', 'setup-postpaid-credit', 'get-all-purchase'] },
      { name: 'DID Management', endpoints: ['get-did-pools', 'get-did-numbers', 'create-did-assignment', 'get-retail-partners'] },
      { name: 'SMS Operations', endpoints: ['rate-plans/*', 'contact-groups/*', 'forbidden-words/*', 'campaigns/*', 'policies/*'] },
    ],
  },
  WebRtc: {
    count: 7,
    categories: [
      { name: 'WebRTC Calling', endpoints: ['/AUTHENTICATION/logout', '/FREESWITCHREST/contact/get-contacts', '/FREESWITCHREST/get-call-history', '/FREESWITCHREST/contact/create-contact', '/FREESWITCHREST/contact/update-contact', '/FREESWITCHREST/contact/delete-contact', '/FREESWITCHREST/api/v1/fusionpbx-contact/list-by-extension'] },
    ],
  },
  SmsSender: {
    count: 1,
    categories: [
      { name: 'SMS', endpoints: ['/SMSREST/sms/send'] },
    ],
  },
  Deny: { endpoints: ['(none — all requests denied)'], count: 0 },
};

export const DATA_ACCESS_RULES = [
  { path: '/get-partner', field: '$.idPartner', authField: 'idPartner', match: 'CONTAINS', description: 'User can only view own partner' },
  { path: '/delete-partner', field: '$.idPartner', authField: 'idPartner', match: 'CONTAINS', description: 'User can only delete own partner' },
  { path: '/update-partner', field: '$.idPartner', authField: 'idPartner', match: 'CONTAINS', description: 'User can only update own partner' },
  { path: '/getUserByEmail', field: '$.email', authField: 'email', match: 'CONTAINS', description: 'User can only query own email' },
  { path: '/getUserByIdPartner', field: '$.idPartner', authField: 'idPartner', match: 'CONTAINS', description: 'User can only query own partner users' },
  { path: '/get-partner-extra', field: '$.id', authField: 'idPartner', match: 'CONTAINS', description: 'User can only view own partner extras' },
  { path: '/partner/get-partner-document', field: '$.partnerId', authField: 'idPartner', match: 'CONTAINS', description: 'User can only view own partner documents' },
  { path: '/editUser', field: '$.id', authField: 'id', match: 'CONTAINS', description: 'User can only edit own account' },
  { path: '/getUser', field: '$.id', authField: 'id', match: 'CONTAINS', description: 'User can only view own account' },
];

export const PUBLIC_ENDPOINT_CATEGORIES = [
  { name: 'Authentication', count: 3, endpoints: ['/AUTHENTICATION/auth/', '/AUTHENTICATION/getUserByEmail', '/AUTHENTICATION/editUser'] },
  { name: 'Partner & OTP', count: 13, endpoints: ['create-partner', 'partner-documents', 'get-partner*', 'update-partner', 'validate', 'otp/send', 'otp/verify', 'email/send', 'email/verify'] },
  { name: 'Packages & Billing', count: 5, endpoints: ['purchase-package', 'topup', 'setup-postpaid-credit', 'getPurchaseForPartner', 'get-all-purchase-partner-wise'] },
  { name: 'PBX — Domains', count: 2, endpoints: ['domains/create', 'domains/get-by-uuid'] },
  { name: 'PBX — Extensions', count: 5, endpoints: ['create', 'list-by-domain', 'get-by-uuid', 'update', 'delete'] },
  { name: 'PBX — Gateways', count: 6, endpoints: ['create', 'list-by-domain', 'get-by-uuid', 'update', 'delete', 'toggle'] },
  { name: 'PBX — Routes', count: 12, endpoints: ['outbound: create/list/get/update/delete/toggle', 'inbound: create/list/get/update/delete/toggle'] },
  { name: 'PBX — Destinations & IVR', count: 11, endpoints: ['destinations: CRUD+toggle', 'ivr: CRUD'] },
  { name: 'PBX — Recordings', count: 7, endpoints: ['create', 'list', 'get', 'update', 'delete', 'download'] },
  { name: 'PBX — Ring Groups', count: 8, endpoints: ['create', 'list', 'get', 'update', 'delete', 'toggle', 'add/delete-destination'] },
  { name: 'PBX — Conferences', count: 5, endpoints: ['create', 'list-by-domain', 'get', 'update', 'delete'] },
  { name: 'Call Center', count: 17, endpoints: ['agents: list/details/status/stats/create/update/delete', 'queues: list/details/status/create/update/delete', 'tiers: list/add/remove'] },
  { name: 'Call Broadcast', count: 7, endpoints: ['list', 'details', 'create', 'update', 'delete', 'start', 'stop', 'upload-leads'] },
  { name: 'CDR & Active Calls', count: 10, endpoints: ['cdr: stats/list/get/filter', 'active-calls: list/details/hangup/transfer/hold/mute'] },
  { name: 'Registrations & Contacts', count: 10, endpoints: ['registrations: list/get/unregister', 'contacts: CRUD', 'call-forwarding: list/update/toggle'] },
  { name: 'Monitoring', count: 1, endpoints: ['/FREESWITCHREST/getConcurrentCall'] },
];

// Fetch audit logs from night-watcher backend API.
// The backend proxies to gateway's MySQL audit_log table.
export async function fetchAuditLogs(token, { search, from, to, page = 0, size = 50 } = {}) {
  const params = new URLSearchParams({ page, size });
  if (search) params.set('search', search);
  if (from) params.set('from', from);
  if (to) params.set('to', to);

  const resp = await fetch(`/api/gateway/audit?${params}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!resp.ok) throw new Error('Failed to fetch audit logs');
  return resp.json();
}
