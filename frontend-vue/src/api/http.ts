import axios, { type AxiosError } from "axios";
import { ElMessage } from "element-plus";
import type { ApiResult } from "../types/common";
import { clearToken, getToken } from "../utils/auth-storage";

export interface ApiBusinessError extends Error {
  code?: string;
}

const http = axios.create({
  baseURL: "/api",
  timeout: 20000
});

http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>;
    if (result && typeof result.success === "boolean") {
      if (result.success) {
        return result.data;
      }
      if (result.code === "UNAUTHORIZED") {
        clearToken();
        if (location.pathname !== "/login") {
          location.href = "/login";
        }
      }
      if (result.code === "PASSWORD_EXPIRED" && location.pathname !== "/login") {
        const url = new URL(location.href);
        if (url.searchParams.get("password") !== "required") {
          url.searchParams.set("password", "required");
          location.href = url.toString();
        }
      }
      if (result.code !== "USER_EMAIL_CLEAR_CONFIRM_REQUIRED") {
        ElMessage.error(result.message || "请求失败");
      }
      const error = new Error(result.message || "Request failed") as ApiBusinessError;
      error.code = result.code;
      return Promise.reject(error);
    }
    return response.data;
  },
  (error: AxiosError<{ message?: string }>) => {
    const status = error.response?.status;
    const message = error.response?.data?.message || error.message || "网络请求失败";
    if (status === 401) {
      clearToken();
      if (location.pathname !== "/login") {
        location.href = "/login";
      }
    }
    ElMessage.error(message);
    return Promise.reject(error);
  }
);

export default http;
