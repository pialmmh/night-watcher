// HA Cluster Management API helpers

const BASE = '/api';

async function api(method, path, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (body) opts.body = JSON.stringify(body);
  const resp = await fetch(`${BASE}${path}`, opts);
  if (!resp.ok) {
    const err = await resp.json().catch(() => ({}));
    throw new Error(err.error || `API error ${resp.status}`);
  }
  return resp.json();
}

// Clusters
export const listClusters = () => api('GET', '/clusters');
export const getCluster = (id) => api('GET', `/clusters/${id}`);
export const getClusterFull = (id) => api('GET', `/clusters/${id}/full`);
export const getClusterStatus = (id) => api('GET', `/clusters/${id}/status`);
export const createCluster = (data) => api('POST', '/clusters', data);
export const updateCluster = (id, data) => api('PUT', `/clusters/${id}`, data);
export const deleteCluster = (id) => api('DELETE', `/clusters/${id}`);

// Nodes
export const listNodes = (clusterId) => api('GET', `/clusters/${clusterId}/nodes`);
export const createNode = (clusterId, data) => api('POST', `/clusters/${clusterId}/nodes`, data);
export const updateNode = (id, data) => api('PUT', `/nodes/${id}`, data);
export const deleteNode = (id) => api('DELETE', `/nodes/${id}`);

// Groups
export const listGroups = (clusterId) => api('GET', `/clusters/${clusterId}/groups`);
export const createGroup = (clusterId, data) => api('POST', `/clusters/${clusterId}/groups`, data);
export const deleteGroup = (id) => api('DELETE', `/groups/${id}`);

// Resources
export const listResources = (groupId) => api('GET', `/groups/${groupId}/resources`);
export const createResource = (groupId, data) => api('POST', `/groups/${groupId}/resources`, data);
export const updateResource = (id, data) => api('PUT', `/resources/${id}`, data);
export const deleteResource = (id) => api('DELETE', `/resources/${id}`);

// Checks
export const listChecks = (groupId) => api('GET', `/groups/${groupId}/checks`);
export const createCheck = (groupId, data) => api('POST', `/groups/${groupId}/checks`, data);
export const updateCheck = (id, data) => api('PUT', `/checks/${id}`, data);
export const deleteCheck = (id) => api('DELETE', `/checks/${id}`);
