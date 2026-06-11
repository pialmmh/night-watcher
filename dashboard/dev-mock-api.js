// Vite plugin: intercepts API calls during dev and returns realistic demo data.
// Only active in dev mode. Production builds ignore this entirely.

const now = Date.now();
const hour = 3600000;

function randomInt(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min; }

function generateTrendData(hours = 24) {
  const data = [];
  for (let i = hours; i >= 0; i--) {
    const ts = now - i * hour;
    data.push({
      key_as_string: new Date(ts).toISOString(),
      key: ts,
      doc_count: randomInt(5, 80),
      watchdog: { doc_count: randomInt(1, 15) },
      security: { doc_count: randomInt(3, 65) },
    });
  }
  return data;
}

const DEMO_EVENTS = Array.from({ length: 50 }, (_, i) => ({
  _id: `evt-${i}`,
  _source: {
    timestamp: new Date(now - i * 120000).toISOString(),
    rule: {
      id: [100002, 100003, 87901, 87902, 31101, 5710, 5503, 2502][i % 8],
      level: [3, 5, 7, 8, 10, 12, 14, 4][i % 8],
      description: [
        'Nginx: access from blocked IP',
        'ModSecurity: SQL injection attempt',
        'CrowdSec: HTTP brute-force',
        'Fail2Ban: SSH brute-force ban',
        'Syscheck: file integrity changed',
        'Authentication failure',
        'ModSecurity: XSS attempt blocked',
        'Nginx: 404 scanner detected',
      ][i % 8],
      groups: [['nginx'], ['modsecurity'], ['crowdsec'], ['fail2ban'], ['syscheck'], ['authentication_failed'], ['modsecurity'], ['nginx']][i % 8],
      mitre: { tactic: ['Initial Access', 'Credential Access', 'Discovery', 'Execution'][i % 4], technique: ['T1190', 'T1110', 'T1046', 'T1059'][i % 4] },
    },
    agent: { name: 'night-watcher-btcl', ip: '10.10.195.200' },
    data: { srcip: ['45.33.12.88', '103.48.16.22', '192.168.1.100', '10.246.7.102'][i % 4], url: ['/admin', '/wp-login.php', '/api/health', '/.env'][i % 4] },
    location: ['/var/log/nginx/access.log', '/var/log/modsec_audit.log', '/var/log/crowdsec.log', '/var/log/auth.log'][i % 4],
    full_log: `[${new Date(now - i * 120000).toISOString()}] demo alert #${i}: ${['Nginx access from blocked IP', 'SQL injection attempt', 'HTTP brute-force detected', 'SSH auth failure'][i % 4]}`,
  },
}));

const esSearchHandler = (body) => {
  const aggs = body?.aggs || body?.aggregations;
  // Severity counts
  if (aggs?.by_level) {
    return {
      hits: { total: { value: 1247 } },
      aggregations: {
        by_level: {
          buckets: [
            { key: 3, doc_count: 412 }, { key: 5, doc_count: 298 },
            { key: 7, doc_count: 187 }, { key: 8, doc_count: 156 },
            { key: 10, doc_count: 89 }, { key: 12, doc_count: 67 },
            { key: 14, doc_count: 38 },
          ],
        },
      },
    };
  }
  // Alert trend
  if (aggs?.over_time) {
    return { aggregations: { over_time: { buckets: generateTrendData() } } };
  }
  // Top rules
  if (aggs?.top_rules) {
    return {
      aggregations: {
        top_rules: {
          buckets: [
            { key: 87901, doc_count: 234, description: { buckets: [{ key: 'ModSecurity: SQL injection attempt' }] }, level: { value: 12 } },
            { key: 100002, doc_count: 189, description: { buckets: [{ key: 'Nginx: access from blocked IP' }] }, level: { value: 8 } },
            { key: 5710, doc_count: 156, description: { buckets: [{ key: 'Authentication failure' }] }, level: { value: 10 } },
            { key: 87902, doc_count: 134, description: { buckets: [{ key: 'ModSecurity: XSS attempt blocked' }] }, level: { value: 12 } },
            { key: 31101, doc_count: 98, description: { buckets: [{ key: 'Syscheck: file integrity changed' }] }, level: { value: 7 } },
            { key: 100003, doc_count: 87, description: { buckets: [{ key: 'CrowdSec: HTTP brute-force' }] }, level: { value: 8 } },
            { key: 5503, doc_count: 76, description: { buckets: [{ key: 'Fail2Ban: SSH brute-force ban' }] }, level: { value: 14 } },
            { key: 2502, doc_count: 65, description: { buckets: [{ key: 'Nginx: 404 scanner detected' }] }, level: { value: 5 } },
          ],
        },
      },
    };
  }
  // MITRE
  if (aggs?.tactics) {
    return {
      aggregations: {
        tactics: { buckets: [
          { key: 'Initial Access', doc_count: 312 }, { key: 'Credential Access', doc_count: 245 },
          { key: 'Discovery', doc_count: 178 }, { key: 'Execution', doc_count: 134 },
          { key: 'Persistence', doc_count: 89 }, { key: 'Defense Evasion', doc_count: 67 },
        ]},
        techniques: { buckets: [
          { key: 'T1190 Exploit Public-Facing App', doc_count: 234 },
          { key: 'T1110 Brute Force', doc_count: 198 },
          { key: 'T1046 Network Service Scanning', doc_count: 156 },
          { key: 'T1059 Command Execution', doc_count: 112 },
        ]},
      },
    };
  }
  // Top IPs
  if (aggs?.top_ips) {
    return {
      aggregations: {
        top_ips: {
          buckets: [
            { key: '45.33.12.88', doc_count: 187, max_level: { value: 14 }, top_rules: { buckets: [{ key: 'SQL injection attempt' }, { key: 'XSS blocked' }] } },
            { key: '103.48.16.22', doc_count: 134, max_level: { value: 12 }, top_rules: { buckets: [{ key: 'HTTP brute-force' }] } },
            { key: '185.220.101.4', doc_count: 98, max_level: { value: 10 }, top_rules: { buckets: [{ key: 'SSH brute-force' }, { key: 'Auth failure' }] } },
            { key: '23.94.12.77', doc_count: 76, max_level: { value: 8 }, top_rules: { buckets: [{ key: '404 scanner' }] } },
            { key: '162.243.17.88', doc_count: 54, max_level: { value: 7 }, top_rules: { buckets: [{ key: 'Nginx blocked' }] } },
          ],
        },
      },
    };
  }
  // Top URLs
  if (aggs?.top_urls) {
    return {
      aggregations: {
        top_urls: {
          buckets: [
            { key: '/wp-login.php', doc_count: 234, top_rules: { buckets: [{ key: 'Brute-force' }] } },
            { key: '/admin', doc_count: 156, top_rules: { buckets: [{ key: 'SQL injection' }] } },
            { key: '/.env', doc_count: 98, top_rules: { buckets: [{ key: 'Info disclosure' }] } },
            { key: '/phpmyadmin/', doc_count: 87, top_rules: { buckets: [{ key: 'Scanner detected' }] } },
            { key: '/api/v1/users', doc_count: 65, top_rules: { buckets: [{ key: 'Rate limit' }] } },
          ],
        },
      },
    };
  }
  // Module summary (by_location)
  if (aggs?.by_location) {
    return {
      aggregations: {
        by_location: {
          buckets: [
            { key: '/var/log/nginx/access.log', doc_count: 456, by_level: { buckets: [{ key: 3, doc_count: 200 }, { key: 5, doc_count: 156 }, { key: 8, doc_count: 100 }] }, top_rules: { buckets: [{ key: 'Nginx: blocked IP', doc_count: 234 }, { key: 'Nginx: 404 scanner', doc_count: 122 }] } },
            { key: '/var/log/modsec_audit.log', doc_count: 312, by_level: { buckets: [{ key: 8, doc_count: 134 }, { key: 12, doc_count: 178 }] }, top_rules: { buckets: [{ key: 'SQL injection', doc_count: 189 }, { key: 'XSS attempt', doc_count: 123 }] } },
            { key: '/var/log/auth.log', doc_count: 189, by_level: { buckets: [{ key: 5, doc_count: 89 }, { key: 10, doc_count: 100 }] }, top_rules: { buckets: [{ key: 'Auth failure', doc_count: 156 }] } },
            { key: '/var/log/crowdsec.log', doc_count: 87, by_level: { buckets: [{ key: 8, doc_count: 87 }] }, top_rules: { buckets: [{ key: 'HTTP brute-force', doc_count: 87 }] } },
          ],
        },
      },
    };
  }
  // WAF by_rule
  if (aggs?.by_rule && body?.query?.bool?.must?.some(m => m.term?.['rule.groups'] === 'modsecurity')) {
    return {
      aggregations: {
        by_rule: {
          buckets: [
            { key: 941100, doc_count: 134, description: { buckets: [{ key: 'XSS Attack Detected via libinjection' }] } },
            { key: 942100, doc_count: 98, description: { buckets: [{ key: 'SQL Injection Attack Detected via libinjection' }] } },
            { key: 932100, doc_count: 56, description: { buckets: [{ key: 'Remote Command Execution' }] } },
            { key: 930100, doc_count: 34, description: { buckets: [{ key: 'Path Traversal Attack' }] } },
          ],
        },
      },
    };
  }
  // Watchdog by_rule
  if (aggs?.by_rule && body?.query?.bool?.must?.some(m => m.term?.['rule.groups'] === 'watchdog')) {
    return {
      aggregations: {
        by_rule: {
          buckets: [
            { key: 100100, doc_count: 45, description: { buckets: [{ key: 'Backend health check: routesphere UP' }] }, latest: { hits: { hits: [{ _source: { timestamp: new Date().toISOString(), data: { status: 'UP' } } }] } } },
            { key: 100101, doc_count: 12, description: { buckets: [{ key: 'Backend health check: sigtran DOWN' }] }, latest: { hits: { hits: [{ _source: { timestamp: new Date(now - 300000).toISOString(), data: { status: 'DOWN' } } }] } } },
          ],
        },
      },
    };
  }
  // Default search (events list)
  const from = body?.from || 0;
  const size = body?.size || 50;
  return {
    hits: {
      total: { value: DEMO_EVENTS.length },
      hits: DEMO_EVENTS.slice(from, from + size),
    },
  };
};

const MODULE_STATUS = {
  processes: {
    nginx: { status: 'RUNNING', detail: 'pid 1234, uptime 3 days 12:34:56' },
    consul: { status: 'RUNNING', detail: 'pid 1235, uptime 3 days 12:34:50' },
    hactl: { status: 'RUNNING', detail: 'pid 1236, uptime 3 days 12:34:45' },
    crowdsec: { status: 'RUNNING', detail: 'pid 1237, uptime 3 days 12:34:40' },
    'fail2ban': { status: 'RUNNING', detail: 'pid 1238, uptime 3 days 12:34:35' },
    'wazuh-indexer': { status: 'RUNNING', detail: 'pid 1239, uptime 3 days 12:34:30' },
    'wazuh-manager': { status: 'RUNNING', detail: 'pid 1240, uptime 3 days 12:34:25' },
    'wazuh-dashboard': { status: 'RUNNING', detail: 'pid 1241, uptime 3 days 12:34:20' },
    'module-status-api': { status: 'RUNNING', detail: 'pid 1242, uptime 3 days 12:34:15' },
    watchdog: { status: 'RUNNING', detail: 'pid 1243, uptime 3 days 12:34:10' },
    'log-shipper': { status: 'RUNNING', detail: 'pid 1244, uptime 3 days 12:34:05' },
  },
  fail2ban: {
    total_jails: 3,
    jails: [
      { name: 'nginx-modsecurity', failed: 12, banned: 2, total_banned: 45, banned_ips: ['45.33.12.88', '103.48.16.22'] },
      { name: 'nginx-botsearch', failed: 5, banned: 1, total_banned: 23, banned_ips: ['185.220.101.4'] },
      { name: 'sshd', failed: 8, banned: 0, total_banned: 12, banned_ips: [] },
    ],
  },
  crowdsec: {
    decision_count: 3,
    alert_count: 7,
    decisions: [
      { value: '45.33.12.88', scenario: 'crowdsecurity/http-bf', duration: '4h', type: 'ban' },
      { value: '103.48.16.22', scenario: 'crowdsecurity/http-crawl-non_statics', duration: '2h', type: 'ban' },
      { value: '185.220.101.0/24', scenario: 'crowdsecurity/ssh-bf', duration: '24h', type: 'ban' },
    ],
    alerts: [
      { scenario: 'crowdsecurity/http-bf', source: { ip: '45.33.12.88' }, events_count: 156 },
      { scenario: 'crowdsecurity/http-crawl-non_statics', source: { ip: '103.48.16.22' }, events_count: 89 },
      { scenario: 'crowdsecurity/ssh-bf', source: { ip: '185.220.101.4' }, events_count: 45 },
    ],
    bouncers: [
      { name: 'cs-firewall-bouncer', revoked: false },
    ],
    collections: {
      collections: [
        { name: 'crowdsecurity/nginx', local_version: '0.2', description: 'Nginx log parser + scenarios', status: 'enabled' },
        { name: 'crowdsecurity/sshd', local_version: '0.3', description: 'SSH brute-force detection', status: 'enabled' },
        { name: 'crowdsecurity/http-cve', local_version: '1.0', description: 'Known CVE exploit detection', status: 'enabled' },
      ],
    },
  },
  nginx: { access_log_lines: 45678, error_log_lines: 234, modsec_log_lines: 312 },
  wazuh: { running_daemons: 5, total_alerts: 12478 },
};

const HACTL_STATUS = {
  nodeId: 'btcl-nw-1',
  state: 'LEADER',
  coordinator: 'btcl-nw-1',
  activeNode: 'btcl-nw-1',
  uptime: '3d 12h 34m',
  sdown: false,
  odown: false,
  selfHealthy: true,
  observations: [
    { nodeId: 'btcl-nw-1', targetNode: 'btcl-nw-1', sdown: false, failCount: 0, selfHealthy: true, age: '2s' },
    { nodeId: 'btcl-nw-2', targetNode: 'btcl-nw-1', sdown: false, failCount: 0, selfHealthy: true, age: '3s' },
    { nodeId: 'btcl-nw-3', targetNode: 'btcl-nw-1', sdown: false, failCount: 0, selfHealthy: true, age: '4s' },
  ],
  groups: [{
    id: 'sigtran-failover',
    resources: [
      { id: 'assign-vip', type: 'vip', state: 'ACTIVE', health: 'HEALTHY', reason: 'VIP assigned' },
      { id: 'manage-sigtran', type: 'action', state: 'ACTIVE', health: 'HEALTHY', reason: 'check passed' },
      { id: 'notify-rs', type: 'action', state: 'ACTIVE', health: 'HEALTHY', reason: 'no check command configured' },
    ],
    checks: [
      { name: 'assign-vip', passed: true, output: 'VIP assigned' },
      { name: 'manage-sigtran', passed: true, output: 'check passed' },
      { name: 'notify-rs', passed: true, output: 'no check command configured' },
    ],
  }],
};

export default function devMockApi() {
  return {
    name: 'dev-mock-api',
    configureServer(server) {
      // OpenSearch proxy mock
      server.middlewares.use('/api/es', (req, res) => {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
          let parsed = {};
          try { parsed = JSON.parse(body); } catch {}

          if (req.url.includes('_cluster/health')) {
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ cluster_name: 'wazuh-cluster', status: 'green', number_of_nodes: 1, active_shards: 12 }));
            return;
          }

          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify(esSearchHandler(parsed)));
        });
      });

      // Module status API mock
      server.middlewares.use('/api/status', (_req, res) => {
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify(MODULE_STATUS));
      });

      // hactl status mock (all node endpoints return same shape)
      server.middlewares.use('/api/hactl', (req, res) => {
        if (req.url.includes('nodes.json')) {
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify([
            { id: 'sbc4', nodeId: 'btcl-nw-1', url: '/api/hactl/sbc4/status' },
            { id: 'dell-slave', nodeId: 'btcl-nw-2', url: '/api/hactl/dell-slave/status' },
            { id: 'sbc1', nodeId: 'btcl-nw-3', url: '/api/hactl/sbc1/status' },
          ]));
          return;
        }

        const status = { ...HACTL_STATUS };
        if (req.url.includes('dell-slave')) {
          status.nodeId = 'btcl-nw-2';
          status.state = 'FOLLOWER';
        } else if (req.url.includes('sbc1')) {
          status.nodeId = 'btcl-nw-3';
          status.state = 'FOLLOWER';
        }

        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify(status));
      });
    },
  };
}
