import type { PageQuery } from "./common";

export interface PlatformAppVO {
  appCode: string;
  appName: string;
}

export interface PlatformSummaryVO {
  totalUsers: number;
  loginEnabledUsers: number;
  accessBindings: number;
  pendingRequests: number;
}

export interface PlatformUserAccessVO {
  userId: number;
  username: string;
  realName: string;
  userType: string;
  deptCode: string;
  deptName: string;
  mobile: string;
  email: string;
  loginEnabled: number;
  appCodes: string[];
}

export interface PlatformUserAccessForm {
  loginEnabled: number;
  appCodes: string[];
}

export interface PlatformUnitVO {
  id: number;
  unitCode: string;
  unitName: string;
  parentId: number | null;
  status: number;
  remark: string;
  appCodes: string[];
  updatedAt: string;
}

export interface PlatformUnitForm {
  id?: number;
  unitCode: string;
  unitName: string;
  parentId: number | null;
  status: number;
  remark: string;
  appCodes: string[];
}

export interface PlatformPermissionRequestVO {
  id: number;
  applicantUserId: number;
  applicantName: string;
  applicantEmail: string;
  applicantMobile: string;
  unitId: number | null;
  unitName: string;
  appCode: string;
  appName: string;
  roleCode: string;
  reason: string;
  status: string;
  reviewerUserId: number | null;
  reviewComment: string;
  reviewedAt: string | null;
  createdAt: string;
}

export interface PlatformPermissionRequestQuery extends PageQuery {
  status?: string;
  appCode?: string;
  applicantUserId?: number | null;
}

export interface PlatformPermissionRequestForm {
  unitId: number | null;
  appCode: string;
  roleCode?: string;
  reason?: string;
}

export interface PlatformPermissionReviewForm {
  action: "APPROVE" | "REJECT";
  comment?: string;
}

export interface PlatformMyAccessVO {
  appCodes: string[];
  units: PlatformUnitVO[];
  recentRequests: PlatformPermissionRequestVO[];
}

export interface PlatformFeedbackAttachmentVO {
  id: number;
  feedbackId: number;
  fileName: string;
  fileExt: string;
  contentType: string;
  fileSize: number;
  fileSha256: string;
  downloadUrl: string;
  createdAt: string;
}

export interface PlatformFeedbackVO {
  id: number;
  feedbackNo: string;
  category: "BUG" | "SUGGESTION" | "DATA" | "OTHER" | string;
  title: string;
  content: string;
  links: string;
  pageUrl: string;
  submitterUserId: number | null;
  submitterUsername: string;
  submitterRealName: string;
  submitterUserType: string;
  submitterDeptName: string;
  submitterSupplierId: number | null;
  attachmentCount: number;
  status: "OPEN" | "PROCESSING" | "RESOLVED" | "IGNORED" | string;
  handlerUserId: number | null;
  handlerName: string;
  handleComment: string;
  handledAt: string | null;
  createdAt: string;
  updatedAt: string;
  attachments: PlatformFeedbackAttachmentVO[];
}

export interface PlatformFeedbackSubmitForm {
  category: string;
  title?: string;
  content?: string;
  links?: string;
  pageUrl?: string;
  images?: File[];
}

export interface PlatformFeedbackQuery extends PageQuery {
  category?: string;
  status?: string;
  keyword?: string;
}

export interface PlatformFeedbackStatusForm {
  status: "OPEN" | "PROCESSING" | "RESOLVED" | "IGNORED" | string;
  handleComment?: string;
}

export interface ProcurementSettingItemVO {
  id?: number;
  settingKey: string;
  settingValue: string | number;
  settingGroup: "API" | "RULE" | string;
  settingLabel: string;
  valueType: "TEXT" | "PASSWORD" | "NUMBER" | "BOOLEAN" | string;
  sortNo: number;
  remark?: string;
}

export interface ProcurementDepartmentLimitVO {
  id?: number;
  deptCode?: string;
  deptName: string;
  singleRequestLimit: number;
  monthlyLimit: number;
  status: number;
  remark?: string;
  updatedAt?: string;
}

export interface ProcurementSettingsVO {
  settings: ProcurementSettingItemVO[];
  departmentLimits: ProcurementDepartmentLimitVO[];
}

export interface ProcurementSettingsUpdateForm {
  settings: ProcurementSettingItemVO[];
  departmentLimits: ProcurementDepartmentLimitVO[];
}

export interface ProcurementRuleTestResultVO {
  actionCode: string;
  actionName: string;
  triggered: boolean;
  message: string;
  detail: Record<string, unknown>;
}

export interface AiKnowledgeBaseVO {
  id: number;
  knowledgeCode: string;
  knowledgeName: string;
  description: string;
  content: string;
  status: number;
  revisionNo: number;
  updatedBy: number;
  updatedAt: string;
}

export interface AiKnowledgeBaseForm {
  knowledgeCode: string;
  knowledgeName: string;
  description: string;
  content: string;
  status: number;
  changeSummary: string;
}

export interface AiSkillVO {
  id: number;
  skillCode: string;
  skillName: string;
  description: string;
  instructions: string;
  knowledgeBaseCodes: string[];
  status: number;
  revisionNo: number;
  updatedBy: number;
  updatedAt: string;
}

export interface AiSkillForm {
  skillCode: string;
  skillName: string;
  description: string;
  instructions: string;
  knowledgeBaseCodes: string[];
  status: number;
  changeSummary: string;
}

export interface AiChangeProposalVO {
  id: number;
  targetType: "KNOWLEDGE_BASE" | "SKILL" | string;
  targetId: number;
  targetCode: string;
  baseRevisionNo: number;
  currentContent: string;
  proposedContent: string;
  rationale: string;
  sourceBizType: string;
  sourceBizId: number | null;
  status: "PENDING" | "APPROVED" | "REJECTED" | string;
  reviewerUserId: number | null;
  reviewerName: string;
  reviewComment: string;
  reviewedAt: string | null;
  createdAt: string;
}

export interface AiContentRevisionVO {
  id: number;
  targetType: string;
  targetId: number;
  targetCode: string;
  revisionNo: number;
  snapshotJson: string;
  changeSource: string;
  changeSummary: string;
  createdBy: number;
  createdAt: string;
}

export interface AiGovernanceOverviewVO {
  knowledgeBases: AiKnowledgeBaseVO[];
  skills: AiSkillVO[];
  proposals: AiChangeProposalVO[];
  pendingProposalCount: number;
}

export interface FeishuApprovalCardNotificationVO {
  id: number;
  approvalTaskId: number;
  purchaseRequestId: number;
  requestNo: string;
  requestTitle: string;
  taskLevelName: string;
  taskStatus: string;
  approverUserId: number;
  approverName: string;
  approverEmail: string;
  recipientUserId: number;
  recipientName: string;
  receiveIdType: string;
  receiveId: string;
  messageId: string;
  status: string;
  action: string;
  retryCount: number;
  lastError: string;
  sentAt: string;
  actedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface FeishuApprovalCardNotificationQuery extends PageQuery {
  status?: string;
  keyword?: string;
  approvalTaskId?: number | null;
  purchaseRequestId?: number | null;
}

export interface SupplierFeishuQuoteNoticeVO {
  id: number;
  purchaseRequestId: number;
  requestNo: string;
  requestTitle: string;
  supplierId: number;
  supplierCode: string;
  supplierName: string;
  noticeType: string;
  bindingId: number | null;
  receiveIdType: string;
  receiveId: string;
  status: string;
  messageId: string;
  lastError: string;
  sentAt: string;
  retryCount: number;
  cardVersion: string;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierFeishuQuoteNoticeQuery extends PageQuery {
  status?: string;
  noticeType?: string;
  keyword?: string;
  purchaseRequestId?: number | null;
  supplierId?: number | null;
}

export interface ProcurementApprovalRouteVO {
  id: number;
  enabled: number;
  routeKey: string;
  roomNature: string;
  roomName: string;
  buildingName: string;
  costCenterCode: string;
  deptCode: string;
  province?: string;
  city?: string;
  districtName?: string;
  townName?: string;
  address?: string;
  deptName: string;
  floorLeaderNames: string;
  floorLeaderRule: string;
  managerNames: string;
  managerRule: string;
  assistantUserIds?: string;
  assistantNames?: string;
  regionName: string;
  regionManagerNames: string;
  regionManagerRule: string;
  directorNames: string;
  directorRule: string;
  poolPushDay: number;
  regionThreshold: number;
  directorThreshold: number;
  overPriceRule: string;
  replacementConfirmNames: string;
  supplierQuoteScope: string;
  remark: string;
  enabledApprovalLevelCount?: number;
  missingApprovalLevelCount?: number;
  approvalPreviewText?: string;
  approvalLevels?: ApprovalLevelVO[];
  updatedAt: string;
}

export interface ApprovalLevelVO {
  id?: number;
  levelNo: number;
  levelCode: string;
  levelName: string;
  roleCode: string;
  enabled: number;
  triggerMode: string;
  passRule: string;
  amountMin?: number | null;
  amountMax?: number | null;
  skipIfEmpty: number;
  candidateUserIds?: string;
  candidateNames?: string;
  candidateUserIdList?: number[];
  remark?: string;
}

export interface ProcurementApprovalRouteForm {
  enabled: number;
  routeKey?: string;
  roomNature?: string;
  roomName: string;
  buildingName: string;
  costCenterCode?: string;
  deptCode?: string;
  province?: string;
  city?: string;
  districtName?: string;
  townName?: string;
  address?: string;
  deptName: string;
  floorLeaderNames: string;
  floorLeaderRule?: string;
  managerNames: string;
  managerRule?: string;
  assistantUserIds?: string;
  assistantNames?: string;
  assistantUserIdList?: number[];
  regionName?: string;
  regionManagerNames?: string;
  regionManagerRule?: string;
  directorNames?: string;
  directorRule?: string;
  poolPushDay: number;
  regionThreshold?: number;
  directorThreshold?: number;
  overPriceRule?: string;
  replacementConfirmNames?: string;
  supplierQuoteScope?: string;
  remark?: string;
  approvalLevels?: ApprovalLevelForm[];
}

export interface ApprovalLevelForm {
  levelNo: number;
  levelCode: string;
  levelName: string;
  roleCode: string;
  enabled: number;
  triggerMode: string;
  passRule: string;
  amountMin?: number | null;
  amountMax?: number | null;
  skipIfEmpty: number;
  candidateUserIds?: string;
  candidateNames?: string;
  candidateUserIdList?: number[];
  remark?: string;
}

export interface ApprovalRouteConfigItemVO {
  code: string;
  name: string;
  source: string;
  scope: string;
  permissions: string;
  rule: string;
  remark: string;
}

export interface ProcurementApprovalRouteConfigVO {
  routes: ProcurementApprovalRouteVO[];
  workflowPaths: ApprovalRouteConfigItemVO[];
  rolePermissions: ApprovalRouteConfigItemVO[];
  approvalRules: ApprovalRouteConfigItemVO[];
  supplierPermissions: ApprovalRouteConfigItemVO[];
  fieldMappings: ApprovalRouteConfigItemVO[];
}

export interface ProcurementApprovalRouteQuery {
  keyword?: string;
  roomName?: string;
  regionName?: string;
  enabled?: number | null;
  routeId?: number | null;
  includeRoutes?: boolean;
  includeRules?: boolean;
  includeApprovalLevels?: boolean;
}

export interface SystemErrorLogVO {
  id: number;
  moduleCode: string;
  functionName: string;
  errorCode: string;
  errorMessage: string;
  exceptionClass: string;
  stackTrace: string;
  requestMethod: string;
  requestUri: string;
  userId: number | null;
  username: string;
  clientIp: string;
  status: "OPEN" | "FIXED" | "IGNORED" | string;
  fixRemark: string;
  fixedBy: number | null;
  fixedAt: string | null;
  occurredAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface SystemErrorLogQuery extends PageQuery {
  moduleCode?: string;
  status?: string;
  keyword?: string;
}

export interface SystemErrorLogStatusForm {
  status: "OPEN" | "FIXED" | "IGNORED";
  fixRemark?: string;
}

export interface ProcurementAiMessageLogVO {
  id: number;
  sourceChannel: string;
  eventType: string;
  messageId: string;
  chatId: string;
  chatType: string;
  messageType: string;
  sourceUserKey: string;
  senderOpenId: string;
  senderUserId: string;
  senderUnionId: string;
  applicantUserId: number | null;
  username: string;
  realName: string;
  sessionId: number | null;
  sessionNo: string;
  candidateNo: number | null;
  monthlyDemandId: number | null;
  monthlyDemandNo: string;
  purchaseRequestId: number | null;
  purchaseRequestNo: string;
  intentCode: string;
  intentName: string;
  processStatus: "RECEIVED" | "SUCCESS" | "FAILED" | "IGNORED" | string;
  rawText: string;
  rawContent: string;
  rawPayload: string;
  difyRequestJson: string;
  difyResponseText: string;
  difyParsedJson: string;
  replyText: string;
  resultSummary: string;
  errorCode: string;
  errorMessage: string;
  durationMs: number | null;
  occurredAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProcurementAiMessageLogQuery extends PageQuery {
  sourceChannel?: string;
  processStatus?: string;
  intentCode?: string;
  messageType?: string;
  sessionNo?: string;
  sourceUserKey?: string;
  keyword?: string;
}

export interface ImportFileLogVO {
  id: number;
  sourceChannel: string;
  importType: string;
  importTypeName: string;
  fileName: string;
  fileExt: string;
  contentType: string;
  fileSize: number;
  fileSha256: string;
  storageMode: string;
  storagePath: string;
  rowCount: number | null;
  successCount: number | null;
  failureCount: number | null;
  processStatus: "RECEIVED" | "SUCCESS" | "FAILED" | string;
  uploaderUserId: number | null;
  uploaderUsername: string;
  uploaderRealName: string;
  sourceUserKey: string;
  messageId: string;
  messageLogId: number | null;
  relatedBizType: string;
  relatedBizId: number | null;
  relatedBizNo: string;
  resultSummary: string;
  errorMessage: string;
  occurredAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface ImportFileLogQuery extends PageQuery {
  sourceChannel?: string;
  importType?: string;
  processStatus?: string;
  keyword?: string;
}

export interface DatabaseTableVO {
  tableName: string;
  tableComment: string;
  engine: string;
  tableCollation: string;
  tableRows: number | null;
  dataLength: number | null;
  indexLength: number | null;
  createTime: string | null;
  updateTime: string | null;
}

export interface DatabaseColumnVO {
  columnName: string;
  dataType: string;
  columnType: string;
  nullable: "YES" | "NO" | string;
  columnKey: string;
  defaultValue: string | null;
  extra: string;
  comment: string;
  ordinalPosition: number;
}

export interface DatabaseTableDetailVO {
  table: DatabaseTableVO;
  columns: DatabaseColumnVO[];
  primaryKeys: string[];
}

export interface DatabaseRowQuery extends PageQuery {
  keyword?: string;
}

export type DatabaseRow = Record<string, unknown>;

export interface DatabaseRowSaveForm {
  values: DatabaseRow;
}

export interface DatabaseRowUpdateForm {
  keyValues: DatabaseRow;
  values: DatabaseRow;
}

export interface DatabaseRowDeleteForm {
  keyValues: DatabaseRow;
}

export interface AssetCatalogItemVO {
  id: number;
  categoryCode: string;
  categoryName: string;
  assetNameCode: string;
  assetName: string;
  depreciationYears?: string | null;
  depreciationMonths?: string | null;
  unit?: string | null;
  inventoryMethod?: string | null;
  physicalNumber?: string | null;
  physicalLabel?: string | null;
  remark?: string | null;
  changeMark?: string | null;
  enabled: number;
  sourceVersion?: string | null;
  sourceRow?: number | null;
  updatedAt?: string | null;
}

export interface AssetCatalogQuery extends PageQuery {
  keyword?: string;
  enabled?: number;
}

export interface AssetCatalogForm {
  categoryCode: string;
  categoryName: string;
  assetNameCode: string;
  assetName: string;
  depreciationYears?: string;
  depreciationMonths?: string;
  unit?: string;
  inventoryMethod?: string;
  physicalNumber?: string;
  physicalLabel?: string;
  remark?: string;
  changeMark?: string;
  enabled: number;
}
