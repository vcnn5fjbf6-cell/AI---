import http from "./http";
import type {
  AuthUserInfo,
  ChangePasswordParams,
  FeishuCallbackParams,
  FeishuLoginUrlResult,
  LoginParams,
  LoginResult
} from "../types/auth";

export function loginApi(params: LoginParams): Promise<LoginResult> {
  return http.post("/auth/login", params);
}

export function getFeishuLoginUrlApi(redirectUri: string): Promise<FeishuLoginUrlResult> {
  return http.get("/auth/feishu/login-url", { params: { redirectUri } });
}

export function feishuCallbackApi(params: FeishuCallbackParams): Promise<LoginResult> {
  return http.post("/auth/feishu/callback", params);
}

export function getMeApi(): Promise<AuthUserInfo> {
  return http.get("/auth/me");
}

export function changePasswordApi(data: ChangePasswordParams): Promise<void> {
  return http.post("/auth/password/change", data);
}

export function logoutApi(): Promise<void> {
  return http.post("/auth/logout");
}
