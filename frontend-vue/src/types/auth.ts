export type UserRole =
  | "SUPER_ADMIN"
  | "ENGINEER"
  | "FLOOR_LEADER"
  | "MANAGER"
  | "SENIOR_MANAGER"
  | "SUPPLIER"
  | "PURCHASER"
  | "REGION_MANAGER";

export interface LoginParams {
  username: string;
  password: string;
  selectedUserId?: number;
}

export interface FeishuCallbackParams {
  code: string;
  state: string;
  redirectUri?: string;
}

export interface FeishuLoginUrlResult {
  url: string;
}

export interface AuthUserInfo {
  userId: number;
  username: string;
  realName: string;
  employeeNo?: string;
  userType: string;
  regionId: number | null;
  deptCode?: string;
  deptName?: string;
  supplierId: number | null;
  dataScopeType: string;
  roleCodes: string[];
  permissionCodes: string[];
  appCodes: string[];
  mustChangePassword?: number;
  passwordChangedAt?: string | null;
  passwordExpiresAt?: string | null;
  passwordExpired?: boolean;
  passwordExpireDays?: number;
}

export interface ChangePasswordParams {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface LoginCandidate {
  userId: number;
  username: string;
  realName: string;
  employeeNo?: string;
  deptName?: string;
}

export interface LoginResult {
  token?: string;
  tokenType?: string;
  expiresIn?: number;
  user?: AuthUserInfo;
  requiresUserSelection?: boolean;
  candidates?: LoginCandidate[];
}
