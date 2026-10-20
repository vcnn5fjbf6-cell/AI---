package com.company.procurement.modules.procurement.workflow.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowDashboardVO {

    private long myRequestTotal;
    private long pendingMyApprovalTotal;
    private long pendingApprovalTotal;
    private long quotingRequestTotal;
    private long comparedRequestTotal;
    private long mySubmittedQuoteTotal;

    private long totalRequestCount;
    private long openRequestCount;
    private long approvedRequestTotal;
    private long returnedRequestTotal;
    private long monthRequestTotal;
    private BigDecimal totalAmount;
    private BigDecimal monthAmount;
    private BigDecimal averageApprovalHours;
    private List<DashboardMetricVO> metrics;
    private List<DashboardBreakdownVO> statusItems;
    private List<DashboardBreakdownVO> departmentItems;
    private List<DashboardBreakdownVO> funnelItems;
    private List<DashboardRequestVO> recentRequests;
    private List<DashboardRequestVO> quoteRequests;
    private List<DashboardProductVO> productItems;
    private List<DashboardSupplierVO> supplierItems;
    private List<DashboardBreakdownVO> cityItems;
    private List<DashboardPurchaseLineVO> purchaseLines;

    @Data
    public static class DashboardMetricVO {
        private String key;
        private String label;
        private String value;
        private String note;
        private String tone;
    }

    @Data
    public static class DashboardBreakdownVO {
        private String key;
        private String label;
        private long count;
        private BigDecimal amount;
        private String tone;
    }

    @Data
    public static class DashboardRequestVO {
        private Long id;
        private String reqNo;
        private String reqTitle;
        private String deptName;
        private String applicantName;
        private String receivingCity;
        private BigDecimal totalAmount;
        private String status;
        private String statusName;
        private String currentNodeName;
        private Integer currentNodeNo;
        private LocalDateTime createdAt;
        private LocalDateTime submittedAt;
        private LocalDateTime dueAt;
        private long quoteCount;
    }

    @Data
    public static class DashboardProductVO {
        private String key;
        private String itemCode;
        private String itemName;
        private String specModel;
        private String unit;
        private BigDecimal requestedQty;
        private BigDecimal awardedQty;
        private BigDecimal requestAmount;
        private BigDecimal awardedAmount;
        private long requestCount;
        private long supplierCount;
    }

    @Data
    public static class DashboardSupplierVO {
        private Long supplierId;
        private String supplierName;
        private long quoteRequestCount;
        private long awardedRequestCount;
        private long awardedItemCount;
        private BigDecimal awardedAmount;
        private BigDecimal winRate;
        private long returnAfterSaleCount;
        private long replacementAfterSaleCount;
        private BigDecimal returnRate;
        private BigDecimal replacementRate;
        private BigDecimal averageLeadTimeDays;
        private String efficiencyName;
        private Integer efficiencyScore;
    }

    @Data
    public static class DashboardPurchaseLineVO {
        private Long requestId;
        private String reqNo;
        private String reqTitle;
        private String deptName;
        private String receivingCity;
        private String itemCode;
        private String itemName;
        private String specModel;
        private BigDecimal qty;
        private String unit;
        private BigDecimal amount;
        private String supplierName;
        private Integer leadTimeDays;
        private String statusName;
        private LocalDateTime occurredAt;
    }
}
