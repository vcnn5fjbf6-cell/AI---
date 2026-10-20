import type { PageResult } from "./common";

export type EhsModuleCode = "HAZARD" | "WASTE" | "PPE" | "INCIDENT" | "WORK_PERMIT";
export type EhsRegistryModule = Exclude<EhsModuleCode, "WORK_PERMIT">;
export type EhsRegistryKey = "hazards" | "waste" | "ppe" | "incidents";

export interface EhsModuleStatusVO {
  moduleCode: EhsModuleCode;
  moduleName: string;
  status: "CONNECTED";
  totalCount: number;
  activeCount: number;
  alertCount: number;
}

export interface EhsRiskItem {
  id: number;
  moduleType: EhsRegistryModule;
  recordNo: string;
  title: string;
  status: string;
  riskLevel?: string;
  deadlineAt?: string;
}

export interface EhsOverviewVO {
  permitTotalCount: number;
  permitApprovingCount: number;
  permitCompletedCount: number;
  myPendingApprovalCount: number;
  knowledgeDocumentCount: number;
  notificationFailureCount: number;
  dataSourceConnected: boolean;
  sharedApprovalAvailable: boolean;
  sharedNotificationAvailable: boolean;
  refreshedAt: string;
  modules: EhsModuleStatusVO[];
  riskQueue: EhsRiskItem[];
}

export interface EhsRegistryRecord {
  id: number;
  recordNo: string;
  moduleType: EhsRegistryModule;
  title: string;
  category?: string;
  subType?: string;
  building?: string;
  location?: string;
  riskLevel?: string;
  status: string;
  ownerUserId?: number;
  ownerName?: string;
  handlerName?: string;
  quantity?: number;
  unit?: string;
  occurredAt?: string;
  deadlineAt?: string;
  completedAt?: string;
  details: Record<string, unknown>;
  remark?: string;
  createdAt: string;
  updatedAt: string;
}

export interface EhsRegistrySavePayload {
  moduleType: EhsRegistryModule;
  title: string;
  category?: string;
  subType?: string;
  building?: string;
  location?: string;
  riskLevel?: string;
  status: string;
  ownerUserId?: number;
  ownerName?: string;
  handlerName?: string;
  quantity?: number;
  unit?: string;
  occurredAt?: string;
  deadlineAt?: string;
  completedAt?: string;
  details: Record<string, unknown>;
  remark?: string;
}

export interface EhsRegistryStats {
  totalCount: number;
  activeCount: number;
  completedCount: number;
  alertCount: number;
  statusCounts: Record<string, number>;
  categoryCounts: Record<string, number>;
}

export type EhsPermitType =
  | "WORK_PERMIT"
  | "HOT_WORK"
  | "HEIGHT_WORK"
  | "CONFINED_SPACE"
  | "ELECTRIC_WORK"
  | "LIFTING_WORK"
  | "EXCAVATION_WORK";

export interface EhsApprovalRecord {
  id: number;
  nodeNo: number;
  nodeName: string;
  approverName?: string;
  action: string;
  approvalComment?: string;
  operatedAt: string;
  toStatus: string;
}

export interface EhsPermit {
  id: number;
  permitNo: string;
  parentId?: number;
  permitType: EhsPermitType;
  title: string;
  applicantUserId: number;
  applicantName: string;
  applicantPhone?: string;
  applicantCompany?: string;
  sourceType: "INTERNAL" | "PUBLIC";
  approvalRouteId: number;
  deptCode?: string;
  deptName?: string;
  contractorName?: string;
  region?: string;
  location: string;
  workContent: string;
  plannedStartAt: string;
  plannedEndAt: string;
  actualStartAt?: string;
  actualEndAt?: string;
  status: string;
  approvalStatus: string;
  currentLevelNo?: number;
  currentApproverId?: number;
  currentApproverName?: string;
  currentLevelName?: string;
  submitRound: number;
  requiredPermitTypes: EhsPermitType[];
  riskItems: Array<Record<string, unknown>>;
  safetyNotice: Record<string, unknown>;
  operationSteps: Array<Record<string, unknown>>;
  signatures: Array<Record<string, unknown>>;
  attachments: Array<Record<string, unknown>>;
  samples: Array<Record<string, unknown>>;
  closure: Record<string, unknown>;
  closeOutcome?: string;
  closeNote?: string;
  remark?: string;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  canSubmit: boolean;
  canApprove: boolean;
  canStart: boolean;
  canClose: boolean;
  children: EhsPermit[];
  approvalRecords: EhsApprovalRecord[];
}

export interface EhsPermitSavePayload {
  approvalRouteId: number | undefined;
  title: string;
  contractorName?: string;
  region?: string;
  location: string;
  workContent: string;
  plannedStartAt: string;
  plannedEndAt: string;
  requiredPermitTypes: EhsPermitType[];
  riskItems: Array<Record<string, unknown>>;
  safetyNotice: Record<string, unknown>;
  operationSteps: Array<Record<string, unknown>>;
  signatures: Array<Record<string, unknown>>;
  attachments: Array<Record<string, unknown>>;
  remark?: string;
}

export interface EhsDepartmentOption {
  approvalRouteId: number;
  deptCode?: string;
  deptName?: string;
  regionName?: string;
  roomName?: string;
  buildingName?: string;
  label: string;
}

export interface EhsPublicPermitSavePayload extends EhsPermitSavePayload {
  applicantName: string;
  applicantPhone: string;
  applicantCompany?: string;
  website?: string;
}

export interface EhsPublicPermitCreated {
  permitId: number;
  permitNo: string;
  accessToken: string;
  accessExpiresAt: string;
}

export interface EhsPublicPermit extends Omit<EhsPermit, "applicantUserId" | "sourceType" | "attachments" | "signatures" | "canApprove" | "children"> {
  applicantUserId?: never;
  sourceType?: never;
  applicantCompany?: string;
  canApprove: boolean;
  canSample: boolean;
  children: EhsPublicPermit[];
}

export interface EhsKnowledgeDocument {
  id: number;
  title: string;
  category?: string;
  fileName?: string;
  fileType?: string;
  content: string;
  uploadedByName?: string;
  updatedAt: string;
}

export interface EhsAiSession {
  id: number;
  title: string;
  updatedAt: string;
}

export interface EhsAiMessage {
  id: number;
  sessionId: number;
  role: "USER" | "ASSISTANT";
  content: string;
  sourceRefsJson?: string;
  createdAt: string;
}

export interface EhsAssistantChatResult {
  sessionId: number;
  answer: string;
  sources: string[];
}

export type EhsRegistryPage = PageResult<EhsRegistryRecord>;
export type EhsPermitPage = PageResult<EhsPermit>;
export type EhsKnowledgePage = PageResult<EhsKnowledgeDocument>;
