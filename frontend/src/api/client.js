export class ApiError extends Error {
  constructor(status, error, message) {
    super(message || error || `HTTP Error ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.error = error;
  }
}

// Token storage: in-memory with sessionStorage fallback
let inMemoryToken = null;
let logoutHandler = null;

export function setLogoutHandler(handler) {
  logoutHandler = handler;
}

export function setAuthToken(token) {
  inMemoryToken = token;
  if (token) {
    try {
      sessionStorage.setItem("algomentor_token", token);
    } catch {
      // Ignore storage errors in restricted environments
    }
  } else {
    try {
      sessionStorage.removeItem("algomentor_token");
    } catch {
      // Ignore
    }
  }
}

export function getAuthToken() {
  if (inMemoryToken) return inMemoryToken;
  try {
    const saved = sessionStorage.getItem("algomentor_token");
    if (saved) {
      inMemoryToken = saved;
      return saved;
    }
  } catch {
    // Ignore
  }
  return null;
}

export function clearAuthToken() {
  inMemoryToken = null;
  try {
    sessionStorage.removeItem("algomentor_token");
  } catch {
    // Ignore
  }
}

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export async function apiClient(endpoint, options = {}) {
  const token = getAuthToken();
  const headers = new Headers(options.headers || {});

  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  // Set Content-Type only if not FormData
  if (!(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const url = endpoint.startsWith("http")
    ? endpoint
    : `${API_BASE_URL}${endpoint}`;

  let response;
  try {
    response = await fetch(url, {
      ...options,
      headers,
    });
  } catch (err) {
    if (err.name === "AbortError") {
      throw new ApiError(408, "Request Timeout", "The request timed out. Please try again.");
    }
    throw new ApiError(503, "Service Unavailable", "Network error. Backend might be unreachable.");
  }

  if (response.status === 204) {
    return null;
  }

  let data = null;
  const contentType = response.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    try {
      data = await response.json();
    } catch {
      data = null;
    }
  } else {
    data = await response.text();
  }

  if (!response.ok) {
    const errorText =
      (data && typeof data === "object" && (data.message || data.error)) ||
      (typeof data === "string" && data) ||
      response.statusText ||
      "Request failed";

    const errorType = (data && data.error) || response.statusText;

    // Auto-logout on 401
    if (response.status === 401) {
      clearAuthToken();
      if (typeof logoutHandler === "function") {
        logoutHandler();
      }
    }

    throw new ApiError(response.status, errorType, errorText);
  }

  return data;
}

