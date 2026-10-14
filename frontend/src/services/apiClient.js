import { parseApiErrorPayload } from "../utils/apiError";

const API_BASE_URL = import.meta.env.VITE_API_URL || "/api";
export const AUTH_STORAGE_KEY = "english-center-auth";

function joinUrl(baseUrl, path) {
  const cleanBase = baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
  const cleanPath = path.startsWith("/") ? path : `/${path}`;
  return `${cleanBase}${cleanPath}`;
}

export function getStoredAuth() {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return null;
    if (typeof parsed.token !== "string" || !parsed.token) return null;
    if (typeof parsed.role !== "string" || !parsed.role) return null;
    if (parsed.id === undefined || parsed.id === null) return null;
    return parsed;
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

function getStoredToken() {
  return getStoredAuth()?.token || null;
}

function clearStoredAuth() {
  localStorage.removeItem(AUTH_STORAGE_KEY);
}

async function readResponse(response) {
  const contentType = response.headers.get("content-type") || "";
  if (response.status === 204) return null;
  if (contentType.includes("application/json")) {
    try {
      return await response.json();
    } catch {
      return null;
    }
  }
  const text = await response.text();
  return text || null;
}

export async function apiFetch(path, options = {}) {
  const token = getStoredToken();
  let response;

  try {
    response = await fetch(joinUrl(API_BASE_URL, path), {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.headers || {})
      }
    });
  } catch {
    throw new Error("Không kết nối được backend. Hãy kiểm tra server hoặc mạng.");
  }

  const payload = await readResponse(response);

  if (!response.ok) {
    if (response.status === 401 && token) {
      clearStoredAuth();
      if (!window.location.pathname.startsWith("/login")) {
        window.location.assign("/login");
      }
    }
    throw new Error(parseApiErrorPayload(payload, response.status));
  }

  return payload;
}

export function apiGet(path) {
  return apiFetch(path);
}

export function apiPost(path, body) {
  return apiFetch(path, {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function apiPut(path, body) {
  return apiFetch(path, {
    method: "PUT",
    body: JSON.stringify(body)
  });
}

export function apiDelete(path) {
  return apiFetch(path, {
    method: "DELETE"
  });
}
export function apiPatch(path, body) {
  return apiFetch(path, {
    method: "PATCH",
    ...(body !== undefined ? { body: JSON.stringify(body) } : {})
  });
}
