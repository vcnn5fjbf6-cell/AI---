import http from "./http";
import type { PageResult } from "../types/common";
import type { PurchaseRequestVO } from "../types/purchase";
import type { SupplierForm, SupplierPageQuery, SupplierVO } from "../types/supplier";
import type {
  SupplierQuoteRequestVO,
  SupplierQuoteSubmitForm,
  SupplierQuoteVO,
  TemporarySupplierRecommendationCreateForm,
  TemporarySupplierRecommendationPageQuery,
  TemporarySupplierRecommendationReviewForm,
  TemporarySupplierRecommendationVO
} from "../types/supplier";

export function getSupplierPageApi(params: SupplierPageQuery): Promise<PageResult<SupplierVO>> {
  return http.get("/admin/suppliers/page", { params });
}

export function getSupplierListApi(params?: Partial<SupplierPageQuery>): Promise<SupplierVO[]> {
  return http.get("/admin/suppliers/list", {
    params: {
      pageNo: 1,
      pageSize: 500,
      ...params
    }
  });
}

export function getSupplierDetailApi(id: number): Promise<SupplierVO> {
  return http.get(`/admin/suppliers/${id}`);
}

export function createSupplierApi(data: SupplierForm): Promise<number> {
  return http.post("/admin/suppliers", data);
}

export function updateSupplierApi(id: number, data: SupplierForm): Promise<void> {
  return http.put(`/admin/suppliers/${id}`, data);
}

export function releaseSupplierBiddingSuspensionApi(id: number): Promise<void> {
  return http.post(`/admin/suppliers/${id}/bidding-suspension/release`);
}

export function banSupplierApi(id: number, data?: { banReason?: string | null }): Promise<void> {
  return http.post(`/admin/suppliers/${id}/ban`, data ?? {});
}

export function unbanSupplierApi(id: number): Promise<void> {
  return http.post(`/admin/suppliers/${id}/unban`);
}

export function deleteSupplierApi(id: number): Promise<void> {
  return http.delete(`/admin/suppliers/${id}`);
}

// ====== 供应商报价相关接口（供应商门户）======

/** 获取可报价的询价请求分页列表 */
export function getQuoteRequestsPageApi(params: Record<string, unknown>): Promise<PageResult<SupplierQuoteRequestVO>> {
  return http.get("/supplier/quote-requests/page", { params });
}

/** 查看询价请求详情 */
export function getQuoteRequestDetailApi(purchaseRequestId: number): Promise<PurchaseRequestVO> {
  return http.get(`/supplier/quote-requests/${purchaseRequestId}`);
}

/** 查看我已提交的报价 */
export function getMyQuoteApi(purchaseRequestId: number): Promise<SupplierQuoteVO | null> {
  return http.get(`/supplier/quotes/${purchaseRequestId}`);
}

/** 查看中标确认详情，仅返回当前供应商中标明细 */
export function getAwardConfirmationDetailApi(purchaseRequestId: number): Promise<SupplierQuoteVO> {
  return http.get(`/supplier/quotes/${purchaseRequestId}/award-confirmation-detail`);
}

/** 提交报价 */
export function submitQuoteApi(data: SupplierQuoteSubmitForm): Promise<SupplierQuoteVO> {
  return http.post("/supplier/quotes", data);
}

export function exportQuoteTemplateApi(rows: string[][]): Promise<Blob> {
  return http.post("/supplier/quotes/template-export", rows, { responseType: "blob" });
}

export function importQuotePreviewApi(file: File): Promise<string[][]> {
  const formData = new FormData();
  formData.append("file", file);
  return http.post("/supplier/quotes/import-preview", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 60000
  });
}

export function confirmAwardApi(purchaseRequestId: number, data?: { confirmRemark?: string | null }): Promise<SupplierQuoteVO> {
  return http.post(`/supplier/quotes/${purchaseRequestId}/award-confirmation`, data ?? {});
}

export function waiveAwardApi(purchaseRequestId: number, data?: { waiveRemark?: string | null }): Promise<SupplierQuoteVO> {
  return http.post(`/supplier/quotes/${purchaseRequestId}/award-waiver`, data ?? {});
}

export function exportAwardApi(purchaseRequestId: number): Promise<Blob> {
  return http.get(`/supplier/quotes/${purchaseRequestId}/award-export`, { responseType: "blob" });
}

// ====== 推荐临时供应商 ======

export function getTemporarySupplierRecommendationsPageApi(
  params: TemporarySupplierRecommendationPageQuery
): Promise<PageResult<TemporarySupplierRecommendationVO>> {
  return http.get("/procurement/temporary-supplier-recommendations/page", { params });
}

export function getTemporarySupplierRecommendationDetailApi(id: number): Promise<TemporarySupplierRecommendationVO> {
  return http.get(`/procurement/temporary-supplier-recommendations/${id}`);
}

export function createTemporarySupplierRecommendationApi(
  data: TemporarySupplierRecommendationCreateForm
): Promise<number> {
  return http.post("/procurement/temporary-supplier-recommendations", data);
}

export function approveTemporarySupplierRecommendationApi(
  id: number,
  data: TemporarySupplierRecommendationReviewForm
): Promise<TemporarySupplierRecommendationVO> {
  return http.post(`/procurement/temporary-supplier-recommendations/${id}/approve`, data);
}

export function rejectTemporarySupplierRecommendationApi(
  id: number,
  data: TemporarySupplierRecommendationReviewForm
): Promise<TemporarySupplierRecommendationVO> {
  return http.post(`/procurement/temporary-supplier-recommendations/${id}/reject`, data);
}
