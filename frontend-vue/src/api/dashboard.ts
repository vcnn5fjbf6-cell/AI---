import { getApprovalRecordPageApi } from "./approval";
import { getPurchaseRequestPageApi } from "./purchase-request";
import { getSupplierPageApi } from "./supplier";

export interface DashboardMetrics {
  purchaseRequestTotal: number;
  pendingRequestTotal: number;
  supplierTotal: number;
  approvalRecordTotal: number;
}

export async function getDashboardMetricsApi(): Promise<DashboardMetrics> {
  const [requestAll, requestPending, supplierPage, approvalPage] = await Promise.all([
    getPurchaseRequestPageApi({ pageNo: 1, pageSize: 1 }),
    getPurchaseRequestPageApi({ pageNo: 1, pageSize: 1, approvalStatus: "PENDING" }),
    getSupplierPageApi({ pageNo: 1, pageSize: 1 }),
    getApprovalRecordPageApi({ pageNo: 1, pageSize: 1 })
  ]);

  return {
    purchaseRequestTotal: requestAll.total,
    pendingRequestTotal: requestPending.total,
    supplierTotal: supplierPage.total,
    approvalRecordTotal: approvalPage.total
  };
}
