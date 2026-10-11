// All data shown in the UI comes from these calls. Nothing is mocked or hard-coded.
const BASE = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
const TOKEN_KEY = "cloudpulse.token";

let token = null;
try {
  token = sessionStorage.getItem(TOKEN_KEY);
} catch {
  token = null;
}
let unauthorizedHandler = () => {};

export function getToken() {
  return token;
}

export function setToken(value) {
  token = value;
  try {
    if (value) sessionStorage.setItem(TOKEN_KEY, value);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable: keep the token in memory only */
  }
}

export function onUnauthorized(handler) {
  unauthorizedHandler = handler;
}

export class ApiError extends Error {
  constructor(message, status, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || {};
  }
}

async function request(path, { method = "GET", body } = {}) {
  let response;
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers: {
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError("Cannot reach the CloudPulse API. Check that the backend is running.", 0);
  }

  if (response.status === 401 && token) {
    setToken(null);
    unauthorizedHandler();
    throw new ApiError("Your session has expired. Sign in again.", 401);
  }

  const isJson = (response.headers.get("content-type") || "").includes("json");
  const payload = isJson ? await response.json().catch(() => null) : null;

  if (!response.ok) {
    throw new ApiError(
      payload?.message || `Request failed with status ${response.status}`,
      response.status,
      payload?.fieldErrors,
    );
  }
  return payload;
}

export const api = {
  login: (username, password) => request("/api/auth/login", { method: "POST", body: { username, password } }),
  summary: () => request("/api/summary"),
  services: () => request("/api/services"),
  createService: (data) => request("/api/services", { method: "POST", body: data }),
  updateService: (id, data) => request(`/api/services/${id}`, { method: "PUT", body: data }),
  setEnabled: (id, enabled) => request(`/api/services/${id}/enabled`, { method: "PATCH", body: { enabled } }),
  deleteService: (id) => request(`/api/services/${id}`, { method: "DELETE" }),
  checkNow: (id) => request(`/api/services/${id}/check-now`, { method: "POST" }),
  checks: (id, limit = 25) => request(`/api/services/${id}/checks?limit=${limit}`),
  latency: (id, hours) => request(`/api/services/${id}/latency?hours=${hours}`),
  metrics: (id, hours = 24) => request(`/api/services/${id}/metrics?hours=${hours}`),
  serviceIncidents: (id) => request(`/api/services/${id}/incidents?limit=20`),
  incidents: (limit = 30) => request(`/api/incidents?status=all&limit=${limit}`),
};
