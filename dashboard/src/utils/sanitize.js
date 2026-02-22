// Replace internal library/tool names with client-friendly labels in display strings.

const REPLACEMENTS = [
  [/\bModSecurity\b/gi, 'WAF'],
  [/\bmodsecurity\b/g, 'waf'],
  [/\bFail2Ban\b/gi, 'Intrusion Prevention'],
  [/\bfail2ban\b/g, 'intrusion-prevention'],
  [/\bCrowdSec\b/gi, 'Threat Intelligence'],
  [/\bcrowdsec\b/g, 'threat-intel'],
  [/\bWazuh\b/gi, 'Security Monitor'],
  [/\bwazuh\b/g, 'security-monitor'],
  [/\bNginx\b/g, 'Web Server'],
  [/\bnginx\b/g, 'web-server'],
];

// Clean a single string (rule description, group name, log line, etc.)
export function sanitizeText(text) {
  if (!text || typeof text !== 'string') return text;
  let result = text;
  for (const [pattern, replacement] of REPLACEMENTS) {
    result = result.replace(pattern, replacement);
  }
  return result;
}

// Clean a location path for display (strip /var/log/ prefix, replace tool names)
export function sanitizeLocation(location) {
  if (!location) return location;
  return sanitizeText(location.replace('/var/log/', ''));
}
