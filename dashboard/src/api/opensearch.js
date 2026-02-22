const ES_BASE = '/api/es';
const INDEX = 'wazuh-alerts-*';

async function esQuery(path, body) {
  const res = await fetch(`${ES_BASE}/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Search API error: ${res.status}`);
  return res.json();
}

async function esGet(path) {
  const res = await fetch(`${ES_BASE}/${path}`);
  if (!res.ok) throw new Error(`Search API error: ${res.status}`);
  return res.json();
}

// Severity count cards (last 24h)
export async function fetchSeverityCounts(timeRange = 'now-24h') {
  const data = await esQuery(`${INDEX}/_search`, {
    size: 0,
    query: { range: { timestamp: { gte: timeRange } } },
    aggs: {
      by_level: { terms: { field: 'rule.level', size: 20 } },
    },
  });
  const buckets = data.aggregations?.by_level?.buckets || [];
  const counts = { critical: 0, high: 0, medium: 0, low: 0 };
  for (const b of buckets) {
    const lvl = b.key;
    if (lvl >= 12) counts.critical += b.doc_count;
    else if (lvl >= 8) counts.high += b.doc_count;
    else if (lvl >= 5) counts.medium += b.doc_count;
    else counts.low += b.doc_count;
  }
  counts.total = data.hits?.total?.value || 0;
  return counts;
}

// Alert trend — hourly buckets over time range
export async function fetchAlertTrend(timeRange = 'now-24h') {
  const data = await esQuery(`${INDEX}/_search`, {
    size: 0,
    query: { range: { timestamp: { gte: timeRange } } },
    aggs: {
      over_time: {
        date_histogram: { field: 'timestamp', fixed_interval: '1h' },
        aggs: {
          watchdog: {
            filter: { term: { 'rule.groups': 'watchdog' } },
          },
          security: {
            filter: {
              bool: { must_not: [{ term: { 'rule.groups': 'watchdog' } }] },
            },
          },
        },
      },
    },
  });
  return (data.aggregations?.over_time?.buckets || []).map((b) => ({
    time: b.key_as_string,
    ts: b.key,
    total: b.doc_count,
    watchdog: b.watchdog.doc_count,
    security: b.security.doc_count,
  }));
}

// Top fired rules
export async function fetchTopRules(timeRange = 'now-24h', size = 10) {
  const data = await esQuery(`${INDEX}/_search`, {
    size: 0,
    query: { range: { timestamp: { gte: timeRange } } },
    aggs: {
      top_rules: {
        terms: { field: 'rule.id', size },
        aggs: {
          description: {
            terms: { field: 'rule.description', size: 1 },
          },
          level: { max: { field: 'rule.level' } },
        },
      },
    },
  });
  return (data.aggregations?.top_rules?.buckets || []).map((b) => ({
    ruleId: b.key,
    count: b.doc_count,
    description: b.description?.buckets?.[0]?.key || `Rule ${b.key}`,
    level: b.level?.value || 0,
  }));
}

// MITRE ATT&CK tactics
export async function fetchMitreTactics(timeRange = 'now-24h') {
  const data = await esQuery(`${INDEX}/_search`, {
    size: 0,
    query: { range: { timestamp: { gte: timeRange } } },
    aggs: {
      tactics: { terms: { field: 'rule.mitre.tactic', size: 20 } },
      techniques: { terms: { field: 'rule.mitre.technique', size: 20 } },
    },
  });
  return {
    tactics: (data.aggregations?.tactics?.buckets || []).map((b) => ({
      name: b.key,
      count: b.doc_count,
    })),
    techniques: (data.aggregations?.techniques?.buckets || []).map((b) => ({
      name: b.key,
      count: b.doc_count,
    })),
  };
}

// Security events (exclude watchdog)
export async function fetchSecurityEvents({
  from = 0,
  size = 50,
  timeRange = 'now-24h',
  levelFilter = null,
  groupFilter = null,
} = {}) {
  const must = [{ range: { timestamp: { gte: timeRange } } }];
  const must_not = [{ term: { 'rule.groups': 'watchdog' } }];
  if (levelFilter) must.push({ range: { 'rule.level': { gte: levelFilter } } });
  if (groupFilter) must.push({ term: { 'rule.groups': groupFilter } });

  const data = await esQuery(`${INDEX}/_search`, {
    from,
    size,
    sort: [{ timestamp: 'desc' }],
    query: { bool: { must, must_not } },
  });
  return {
    total: data.hits?.total?.value || 0,
    events: (data.hits?.hits || []).map((h) => ({ id: h._id, ...h._source })),
  };
}

// WAF events
export async function fetchWafEvents({
  from = 0,
  size = 50,
  timeRange = 'now-24h',
} = {}) {
  const data = await esQuery(`${INDEX}/_search`, {
    from,
    size,
    sort: [{ timestamp: 'desc' }],
    query: {
      bool: {
        must: [
          { range: { timestamp: { gte: timeRange } } },
          { term: { 'rule.groups': 'modsecurity' } },
        ],
      },
    },
  });
  return {
    total: data.hits?.total?.value || 0,
    events: (data.hits?.hits || []).map((h) => ({ id: h._id, ...h._source })),
  };
}

// WAF attack type breakdown
export async function fetchWafAttackTypes(timeRange = 'now-24h') {
  const data = await esQuery(`${INDEX}/_search`, {
    size: 0,
    query: {
      bool: {
        must: [
          { range: { timestamp: { gte: timeRange } } },
          { term: { 'rule.groups': 'modsecurity' } },
        ],
      },
    },
    aggs: {
      by_rule: {
        terms: { field: 'rule.id', size: 10 },
        aggs: {
          description: { terms: { field: 'rule.description', size: 1 } },
        },
      },
    },
  });
  return (data.aggregations?.by_rule?.buckets || []).map((b) => ({
    ruleId: b.key,
    count: b.doc_count,
    description: b.description?.buckets?.[0]?.key || `Rule ${b.key}`,
  }));
}

// Watchdog events
export async function fetchWatchdogEvents({
  from = 0,
  size = 100,
  timeRange = 'now-24h',
} = {}) {
  const data = await esQuery(`${INDEX}/_search`, {
    from,
    size,
    sort: [{ timestamp: 'desc' }],
    query: {
      bool: {
        must: [
          { range: { timestamp: { gte: timeRange } } },
          { term: { 'rule.groups': 'watchdog' } },
        ],
      },
    },
  });
  return {
    total: data.hits?.total?.value || 0,
    events: (data.hits?.hits || []).map((h) => ({ id: h._id, ...h._source })),
  };
}

// Watchdog status summary
export async function fetchWatchdogStatus(timeRange = 'now-1h') {
  const data = await esQuery(`${INDEX}/_search`, {
    size: 0,
    query: {
      bool: {
        must: [
          { range: { timestamp: { gte: timeRange } } },
          { term: { 'rule.groups': 'watchdog' } },
        ],
      },
    },
    aggs: {
      by_rule: {
        terms: { field: 'rule.id', size: 10 },
        aggs: {
          description: { terms: { field: 'rule.description', size: 1 } },
          latest: { top_hits: { size: 1, sort: [{ timestamp: 'desc' }] } },
        },
      },
    },
  });
  return (data.aggregations?.by_rule?.buckets || []).map((b) => ({
    ruleId: b.key,
    count: b.doc_count,
    description: b.description?.buckets?.[0]?.key || `Rule ${b.key}`,
    latest: b.latest?.hits?.hits?.[0]?._source || null,
  }));
}

// Top source IPs
export async function fetchTopIPs(timeRange = 'now-24h', size = 20) {
  const data = await esQuery(`${INDEX}/_search`, {
    size: 0,
    query: {
      bool: {
        must: [
          { range: { timestamp: { gte: timeRange } } },
          { exists: { field: 'data.srcip' } },
        ],
        must_not: [{ term: { 'rule.groups': 'watchdog' } }],
      },
    },
    aggs: {
      top_ips: {
        terms: { field: 'data.srcip', size },
        aggs: {
          top_rules: { terms: { field: 'rule.description', size: 3 } },
          max_level: { max: { field: 'rule.level' } },
        },
      },
    },
  });
  return (data.aggregations?.top_ips?.buckets || []).map((b) => ({
    ip: b.key,
    count: b.doc_count,
    maxLevel: b.max_level?.value || 0,
    topRules: (b.top_rules?.buckets || []).map((r) => r.key),
  }));
}

// Top URLs triggering alerts
export async function fetchTopURLs(timeRange = 'now-24h', size = 20) {
  const data = await esQuery(`${INDEX}/_search`, {
    size: 0,
    query: {
      bool: {
        must: [
          { range: { timestamp: { gte: timeRange } } },
          { exists: { field: 'data.url' } },
        ],
        must_not: [{ term: { 'rule.groups': 'watchdog' } }],
      },
    },
    aggs: {
      top_urls: {
        terms: { field: 'data.url', size },
        aggs: {
          top_rules: { terms: { field: 'rule.description', size: 3 } },
        },
      },
    },
  });
  return (data.aggregations?.top_urls?.buckets || []).map((b) => ({
    url: b.key,
    count: b.doc_count,
    topRules: (b.top_rules?.buckets || []).map((r) => r.key),
  }));
}

// Cluster health check
export async function fetchClusterHealth() {
  return esGet('_cluster/health');
}

// ─── Log Explorer: generic log search with module/level/text filters ────────

const MODULE_LOCATIONS = {
  nginx: '/var/log/nginx/access.log',
  waf: '/var/log/modsec_audit.log',
  fail2ban: '/var/log/fail2ban.log',
  crowdsec: '/var/log/crowdsec.log',
  watchdog: '/var/log/security-bundle/watchdog.log',
  auth: '/var/log/auth.log',
  syscheck: 'syscheck',
  system: 'wazuh-monitord',
};

export function getModuleNames() {
  return Object.keys(MODULE_LOCATIONS);
}

export async function fetchLogs({
  from = 0,
  size = 100,
  timeRange = 'now-24h',
  module = null,      // 'nginx' | 'waf' | 'watchdog' | 'system' | null=all
  levelMin = null,    // minimum rule.level
  levelMax = null,    // maximum rule.level
  searchText = null,  // full-text search in full_log
  ruleId = null,      // specific rule.id
  srcIp = null,       // specific data.srcip
  sortField = 'timestamp',
  sortDir = 'desc',
} = {}) {
  const must = [{ range: { timestamp: { gte: timeRange } } }];
  const should = [];

  if (module && MODULE_LOCATIONS[module]) {
    must.push({ term: { location: MODULE_LOCATIONS[module] } });
  }
  if (levelMin != null || levelMax != null) {
    const range = {};
    if (levelMin != null) range.gte = levelMin;
    if (levelMax != null) range.lte = levelMax;
    must.push({ range: { 'rule.level': range } });
  }
  if (searchText) {
    must.push({ match_phrase: { full_log: searchText } });
  }
  if (ruleId) {
    must.push({ term: { 'rule.id': ruleId } });
  }
  if (srcIp) {
    must.push({ term: { 'data.srcip': srcIp } });
  }

  const data = await esQuery(`${INDEX}/_search`, {
    from,
    size,
    sort: [{ [sortField]: sortDir }],
    query: { bool: { must } },
    highlight: searchText ? {
      fields: { full_log: { fragment_size: 300, number_of_fragments: 1 } },
      pre_tags: ['<mark>'],
      post_tags: ['</mark>'],
    } : undefined,
  });

  return {
    total: data.hits?.total?.value || 0,
    events: (data.hits?.hits || []).map((h) => ({
      id: h._id,
      ...h._source,
      _highlight: h.highlight?.full_log?.[0] || null,
    })),
  };
}

// Module-level summary (counts per module + level breakdown)
export async function fetchModuleSummary(timeRange = 'now-24h') {
  const data = await esQuery(`${INDEX}/_search`, {
    size: 0,
    query: { range: { timestamp: { gte: timeRange } } },
    aggs: {
      by_location: {
        terms: { field: 'location', size: 20 },
        aggs: {
          by_level: { terms: { field: 'rule.level', size: 20 } },
          top_rules: {
            terms: { field: 'rule.description', size: 5 },
          },
        },
      },
    },
  });

  const locationToModule = {};
  for (const [mod, loc] of Object.entries(MODULE_LOCATIONS)) {
    locationToModule[loc] = mod;
  }

  return (data.aggregations?.by_location?.buckets || []).map((b) => ({
    location: b.key,
    module: locationToModule[b.key] || 'other',
    count: b.doc_count,
    levels: Object.fromEntries(
      (b.by_level?.buckets || []).map((l) => [l.key, l.doc_count])
    ),
    topRules: (b.top_rules?.buckets || []).map((r) => ({
      description: r.key,
      count: r.doc_count,
    })),
  }));
}
