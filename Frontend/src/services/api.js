import axios from "axios";
import { API_BASE_URL } from "../config";

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

const getTokenKeyForUrl = (url = "") => {
  if (url.startsWith("/admin")) return "admin_token";
  if (url.startsWith("/hrs")) return "hr_token";
  if (url.startsWith("/candidates") || url.startsWith("/assessment")) {
    return "candidate_token";
  }
  if (url.startsWith("/jobs")) {
    if (url.includes("/responses")) return "candidate_token";
    const role = (
      localStorage.getItem("user_role") ||
      localStorage.getItem("role") ||
      ""
    ).toLowerCase();
    if (role === "admin") return "admin_token";
    if (role === "candidate") return "candidate_token";
    return "hr_token";
  }
  return "token";
};

api.interceptors.request.use(
  (config) => {
    const tokenKey = getTokenKeyForUrl(config.url || "");
    let token = localStorage.getItem(tokenKey);

    if (!token && tokenKey !== "token") {
      const activeRole = localStorage.getItem("user_role");
      const roleForToken = tokenKey.replace("_token", "");
      if (activeRole === roleForToken) {
        token = localStorage.getItem("token");
        if (token) {
          localStorage.setItem(tokenKey, token);
        }
      }
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error),
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      console.warn("Unauthorized! Redirecting to login...");
      const tokenKey = getTokenKeyForUrl(error.config?.url || "");
      localStorage.removeItem(tokenKey);
      if (tokenKey === "token") {
        localStorage.removeItem("token");
      }
    }
    return Promise.reject(error);
  },
);

export default api;
