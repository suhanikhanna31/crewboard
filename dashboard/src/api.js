export const BASE = import.meta.env.VITE_API_URL || "http://localhost:8000";
let token = null;

async function req(path, opts = {}) {
  const res = await fetch(BASE + path, {
    ...opts,
    headers: { "Content-Type": "application/json", ...(token && { Authorization: `Bearer ${token}` }) },
  });
  if (!res.ok) {
    const detail = await res.json().catch(() => ({}));
    throw new Error(detail.detail || `${res.status} ${res.statusText}`);
  }
  return res.json();
}

export async function login(username, password) {
  const data = await req("/auth/login", { method: "POST", body: JSON.stringify({ username, password }) });
  token = data.access_token;
  return data;
}

export const wsUrl = () => `${BASE.replace(/^http/, "ws")}/ws?token=${token}`;

export const api = {
  tasks: () => req("/tasks"),
  resources: () => req("/resources"),
  utilisation: () => req("/metrics/utilisation"),
  createTask: (t) => req("/tasks", { method: "POST", body: JSON.stringify(t) }),
  assign: (taskId, resourceId) =>
    req(`/tasks/${taskId}/assign`, { method: "POST", body: JSON.stringify({ resource_id: resourceId }) }),
};
