import type { PageQuery } from "./common";

export interface ApprovalRecordPageQuery extends PageQuery {
  instanceId?: number | null;
  purchaseRequestId?: number | null;
  approverUserId?: number | null;
  action?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

export interface ApprovalRecordVO {
  id: number;
  instanceId: number;
  purchaseRequestId: number;
  nodeNo: number;
  nodeName: string;
  approverUserId: number;
  approverName?: string;
  approverRoleCode: string;
  action: string;
  fromStatus: string;
  toStatus: string;
  approvalComment: string;
  operatedAt: string;
  submitRound: number;
  isFinalNode: number;
  canResubmit: number;
  transferToUserId: number | null;
  transferToUserName?: string;
  status: string;
  remark: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApprovalActionForm {
  instanceId: number;
  purchaseRequestId: number;
  nodeNo: number;
  nodeName: string;
  approverUserId: number;
  approverRoleCode: string;
  action: string;
  fromStatus: string;
  toStatus: string;
  approvalComment: string;
  submitRound: number;
  isFinalNode: number;
  canResubmit: number;
  transferToUserId: number | null;
  status: string;
  remark: string;
}
