import { defineStore } from "pinia";
import { feishuCallbackApi, getMeApi, loginApi, logoutApi } from "../api/auth";
import type { AuthUserInfo, FeishuCallbackParams, LoginParams, LoginResult } from "../types/auth";
import { clearToken, getToken, setToken } from "../utils/auth-storage";
const HR_LOCAL_PROFILE_KEY = "hr_local_profile";

interface AuthState {
  token: string;
  user: AuthUserInfo | null;
  profileLoaded: boolean;
}

function resolveEffectiveRoleCodes(user: AuthUserInfo | null): string[] {
  if (!user) {
    return [];
  }
  const codes = new Set<string>();
  for (const code of user.roleCodes ?? []) {
    if (code) {
      codes.add(code.trim().toUpperCase());
    }
  }
  if (user.userType) {
    codes.add(user.userType.trim().toUpperCase());
  }
  if (user.supplierId != null) {
    codes.add("SUPPLIER");
  }
  return Array.from(codes);
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    token: getToken(),
    user: null,
    profileLoaded: false
  }),

  getters: {
    isLoggedIn: (state): boolean => Boolean(state.token),
    username: (state): string => state.user?.username ?? "",
    realName: (state): string => state.user?.realName ?? "",
    roleCodes: (state): string[] => state.user?.roleCodes ?? [],
    effectiveRoleCodes: (state): string[] => resolveEffectiveRoleCodes(state.user),
    permissionCodes: (state): string[] => state.user?.permissionCodes ?? [],
    appCodes: (state): string[] => state.user?.appCodes ?? []
  },

  actions: {
    async login(params: LoginParams): Promise<LoginResult> {
      const result = await loginApi(params);
      if (result.requiresUserSelection) {
        return result;
      }
      if (!result.token || !result.user) {
        throw new Error("登录结果缺少用户会话");
      }
      this.token = result.token;
      setToken(result.token);
      this.user = result.user;
      this.profileLoaded = true;
      return result;
    },

    async loginByFeishu(params: FeishuCallbackParams): Promise<void> {
      const result = await feishuCallbackApi(params);
      if (!result.token || !result.user) {
        throw new Error("飞书登录结果缺少用户会话");
      }
      this.token = result.token;
      setToken(result.token);
      this.user = result.user;
      this.profileLoaded = true;
    },

    loginWithHrLocalUser(username: string, password: string): boolean {
      try {
        const users = JSON.parse(localStorage.getItem("personnel_users") || "{}") as Record<string, { password?: string; name?: string; role?: string }>;
        let stored = users[username];
        if (!stored && username === "admin" && password === "admin123") {
          stored = { password: "admin123", name: "管理员", role: "admin" };
        }
        if (!stored || stored.password !== password) {
          return false;
        }
        const isAdmin = stored.role === "admin";
        const user: AuthUserInfo = {
          userId: 100000 + Object.keys(users).indexOf(username),
          username,
          realName: stored.name || username,
          userType: isAdmin ? "SUPER_ADMIN" : "ENGINEER",
          regionId: 1,
          supplierId: null,
          dataScopeType: "ALL",
          roleCodes: [isAdmin ? "SUPER_ADMIN" : "ENGINEER"],
          permissionCodes: ["*:*:*"],
          appCodes: ["MAINTENANCE", "PROCUREMENT", "PLATFORM"],
          mustChangePassword: 0
        };
        this.token = `hr-local-${username}`;
        setToken(this.token);
        localStorage.setItem(HR_LOCAL_PROFILE_KEY, JSON.stringify(user));
        this.user = user;
        this.profileLoaded = true;
        return true;
      } catch {
        return false;
      }
    },
    async fetchProfile(): Promise<void> {
      if (!this.token) {
        return;
      }
      if (this.profileLoaded && this.user) {
        return;
      }
      if (this.token.startsWith("hr-local-")) {
        const raw = localStorage.getItem(HR_LOCAL_PROFILE_KEY);
        if (raw) {
          this.user = JSON.parse(raw) as AuthUserInfo;
        } else {
          const username = this.token.replace("hr-local-", "");
          this.user = {
            userId: 999999,
            username,
            realName: username,
            userType: "ENGINEER",
            regionId: 1,
            supplierId: null,
            dataScopeType: "ALL",
            roleCodes: ["ENGINEER"],
            permissionCodes: ["*:*:*"],
            appCodes: ["MAINTENANCE", "PROCUREMENT", "PLATFORM"],
            mustChangePassword: 0
          };
        }
        this.profileLoaded = true;
        return;
      }
      this.user = await getMeApi();
      this.profileLoaded = true;
    },

    hasAnyRole(roles: string[]): boolean {
      if (!roles || roles.length === 0) {
        return true;
      }
      if (!this.user) {
        return false;
      }
      const normalizedRoles = roles.map((role) => role.trim().toUpperCase());
      const effectiveRoleCodes = resolveEffectiveRoleCodes(this.user);
      return effectiveRoleCodes.some((code) => normalizedRoles.includes(code));
    },

    hasAnyPermission(perms: string[]): boolean {
      if (!perms || perms.length === 0) {
        return true;
      }
      if (!this.user) {
        return false;
      }
      if (!this.permissionCodes || this.permissionCodes.length === 0) {
        return false;
      }
      if (this.permissionCodes.includes("*") || this.permissionCodes.includes("*:*:*")) {
        return true;
      }
      return perms.some((perm) => this.permissionCodes.includes(perm));
    },

    hasApp(appCode: string): boolean {
      const normalizedAppCode = appCode?.trim().toUpperCase();
      if (!normalizedAppCode) {
        return true;
      }
      const normalizedAppCodes = this.appCodes.map((code) => code.trim().toUpperCase());
      return normalizedAppCodes.includes(normalizedAppCode);
    },

    async logout(): Promise<void> {
      try {
        if (this.token) {
          await logoutApi();
        }
      } finally {
        this.logoutLocal();
      }
    },

    logoutLocal(): void {
      localStorage.removeItem(HR_LOCAL_PROFILE_KEY);
      this.token = "";
      this.user = null;
      this.profileLoaded = false;
      clearToken();
    }
  }
});
