package com.company.procurement.modules.procurement.workflow.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestPageQueryDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestUpdateDTO;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalActionDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowQuoteConfirmDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowQuoteDeadlineExtendDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowReplacementDecisionDTO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowDashboardVO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteSummaryVO;

import java.util.List;

public interface WorkflowService {

    PageResult<PurchaseRequestVO> pageVisibleRequests(PurchaseRequestPageQueryDTO queryDTO);

    List<PurchaseRequestVO> rejectedItemMessages();

    List<String> rejectedMessageReadKeys();

    void markRejectedMessagesRead(List<String> messageKeys);

    List<String> rejectedMessageClearedKeys();

    void clearRejectedMessages(List<String> messageKeys);

    PurchaseRequestVO detailVisibleRequest(Long id);

    Long createRequest(PurchaseRequestCreateDTO dto);

    void updateRequest(Long id, PurchaseRequestUpdateDTO dto);

    void closeReturnedRequest(Long id);

    WorkflowSubmitResultVO previewSubmitRequest(Long id);

    WorkflowSubmitResultVO submitRequest(Long id);

    PageResult<PurchaseRequestVO> pageApprovalTasks(PurchaseRequestPageQueryDTO queryDTO);

    WorkflowSubmitResultVO approveRequest(Long purchaseRequestId, WorkflowApprovalActionDTO dto);

    WorkflowSubmitResultVO confirmReplacementItems(Long purchaseRequestId, boolean replacementApproved, String comment);

    WorkflowSubmitResultVO confirmReplacementItems(Long purchaseRequestId, List<WorkflowReplacementDecisionDTO> decisions, String comment);

    void addSignRequest(Long purchaseRequestId, Long addSignUserId, String comment);

    int publishDemandPoolRequests();

    int publishDemandPoolRequestsForTest();

    Long publishDemandPoolRequestForFlowTest(Long purchaseRequestId);

    int publishDemandPoolRequestsOnSchedule();

    int processApprovalTimeoutsForTest();

    WorkflowDashboardVO getDashboard();

    List<ApprovalRecordVO> listApprovalRecords(Long purchaseRequestId);

    SupplierQuoteSummaryVO getQuoteSummary(Long purchaseRequestId);

    SupplierQuoteSummaryVO confirmWinningQuote(Long purchaseRequestId, WorkflowQuoteConfirmDTO dto);

    PurchaseRequestVO extendQuoteDeadline(Long purchaseRequestId, WorkflowQuoteDeadlineExtendDTO dto);

    ExportFile exportQuoteComparison(Long purchaseRequestId);
}
