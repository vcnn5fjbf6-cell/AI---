import type { PageQuery } from "./common";

export interface UserPageQuery extends PageQuery {
  username?: string;
  realName?: string;
  employeeNo?: string;
  userType?: string;
  accountCategory?: "EMPLOYEE" | "SUPPLIER";
  regionId?: number | null;
  supplierId?: number | null;
  status?: number | null;
}

export interface UserVO {
  id: number;
  username: string;
  realName: string;
  employeeNo?: string;
  userType: string;
  supplierId: number | null;
  supplierCode?: string;
  supplierName?: string;
  regionId: number | null;
  deptCode: string;
  deptName: string;
  mobile: string;
  email: string;
  feishuUserId?: string;
  feishuOpenId?: string;
  feishuUnionId?: string;
  status: number;
  dataScopeType: string;
  mustChangePassword: number;
  remark: string;
  lastLoginAt: string;
  lastLoginIp: string;
  roleIds: number[];
  createdAt: string;
  updatedAt: string;
}

export interface UserForm {
  username?: string;
  password?: string;
  realName: string;
  employeeNo?: string;
  userType: string;
  supplierId: number | null;
  regionId: number | null;
  deptCode: string;
  deptName: string;
  mobile: string;
  email: string;
  feishuUserId?: string;
  feishuOpenId?: string;
  feishuUnionId?: string;
  status: number;
  dataScopeType: string;
  mustChangePassword: number;
  remark: string;
  roleIds: number[];
  confirmClearOccupiedEmail?: boolean;
}

export type UserOffboardingImpactType = "APPROVAL_CONFIG" | "PENDING_TASK";

export interface UserOffboardingImpactItem {
  impactKey: string;
  impactType: UserOffboardingImpactType;
  referenceId: number;
  levelId: number | null;
  scopeCode: string | null;
  scopeName: string | null;
  levelCode: string | null;
  levelName: string | null;
  roleCode: string | null;
  nodeEnabled: number;
  businessNo: string | null;
  businessTitle: string | null;
}

export interface UserOffboardingImpact {
  userId: number;
  userName: string;
  status: number;
  approvalConfigCount: number;
  pendingTaskCount: number;
  impacts: UserOffboardingImpactItem[];
}

export interface UserOffboardingPayload {
  replacements: Array<{
    impactKey: string;
    replacementUserId: number;
  }>;
}

export interface FeishuUserSyncPayload {
  rootDepartmentId?: string;
  recursive?: boolean;
  createMissing?: boolean;
  updateExisting?: boolean;
  includeInactive?: boolean;
  defaultUserType?: string;
  defaultRegionId?: number | null;
}

export interface FeishuUserSyncResult {
  syncedAt: string;
  departmentCount: number;
  feishuUserCount: number;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
  warnings: string[];
}

export interface FeishuDepartmentSearchResult {
  name: string;
  departmentId?: string;
  openDepartmentId?: string;
  parentDepartmentId?: string;
  path?: string;
}

export interface UserImportResult {
  totalCount: number;
  successCount: number;
  createdCount: number;
  updatedCount: number;
  failedCount: number;
  errors: string[];
}

export interface RolePageQuery extends PageQuery {
  roleCode?: string;
  roleName?: string;
  status?: number | null;
}

export interface RoleVO {
  id: number;
  roleCode: string;
  roleName: string;
  roleType: string;
  dataScopeType: string;
  approvalAmountMin: number | null;
  approvalAmountMax: number | null;
  status: number;
  description: string;
  permissionIds: number[];
  createdAt: string;
  updatedAt: string;
}

export interface RoleForm {
  roleCode: string;
  roleName: string;
  roleType: string;
  dataScopeType: string;
  approvalAmountMin: number | null;
  approvalAmountMax: number | null;
  status: number;
  description: string;
  permissionIds: number[];
}

export interface PermissionVO {
  id: number;
  permCode: string;
  permName: string;
  moduleCode: string;
  permType: string;
  parentId: number | null;
  path: string;
  httpMethod: string;
  sortNo: number;
  status: number;
  description: string;
}
