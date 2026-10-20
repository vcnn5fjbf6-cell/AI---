package com.company.procurement.modules.supplier.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TemporarySupplierRecommendationVO {

    private Long id;
    private String recommendationNo;
    private Long purchaseRequestId;
    private String requestNo;
    private String requestTitle;
    private Long regionId;
    private String deptCode;
    private String deptName;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private Long recommendedBy;
    private String recommendedByName;
    private String recommendReason;
    private String status;
    private String statusText;
    private String currentRoleCode;
    private String currentRoleName;
    private Boolean currentUserCanReview;
    private Long roomManagerUserId;
    private String roomManagerName;
    private String roomManagerComment;
    private LocalDateTime roomManagerReviewedAt;
    private Long regionManagerUserId;
    private String regionManagerName;
    private String regionManagerComment;
    private LocalDateTime regionManagerReviewedAt;
    private Long rejectedBy;
    private String rejectedByName;
    private String rejectReason;
    private LocalDateTime rejectedAt;
    private String remark;
    private Integer itemCount;
    private List<TemporarySupplierRecommendationItemVO> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
