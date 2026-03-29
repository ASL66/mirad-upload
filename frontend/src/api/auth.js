import http from "./http";

export function checkSession() {
  return http.get("/api/auth/session");
}

export function login(username, password) {
  const body = new URLSearchParams({ username, password });
  return http.post("/api/auth/login", body);
}

export function register(username, password) {
  const body = new URLSearchParams({ username, password });
  return http.post("/api/auth/register", body);
}

export function logout() {
  return http.post("/api/auth/logout");
}
