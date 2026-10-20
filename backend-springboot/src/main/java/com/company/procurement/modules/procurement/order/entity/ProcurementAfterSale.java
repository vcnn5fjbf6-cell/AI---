package com.company.procurement.modules.procurement.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("procurement_after_sale")
public class ProcurementAfterSale extends BaseEntity {

    @TableField("after_sale_no")
    private String afterSaleNo;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("purchase_request_item_id")
    private Long purchaseRequestItemId;

    @TableField("quote_id")
    private Long quoteId;

    @TableField("quote_item_id")
    private Long quoteItemId;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("requester_user_id")
    private Long requesterUserId;

    @TableField("after_sale_type")
    private String afterSaleType;

    @TableField("reason")
    private String reason;

    @TableField("requested_qty")
    private BigDecimal requestedQty;

    @TableField("replacement_item_code")
    private String replacementItemCode;

    @TableField("replacement_item_name")
    private String replacementItemName;

    @TableField("replacement_spec_model")
    private String replacementSpecModel;

    @TableField("replacement_unit")
    private String replacementUnit;

    @TableField("replacement_remark")
    private String replacementRemark;

    @TableField("supplier_proposal_type")
    private String supplierProposalType;

    @TableField("supplier_proposal_remark")
    private String supplierProposalRemark;

    @TableField("supplier_replacement_item_code")
    private String supplierReplacementItemCode;

    @TableField("supplier_replacement_item_name")
    private String supplierReplacementItemName;

    @TableField("supplier_replacement_spec_model")
    private String supplierReplacementSpecModel;

    @TableField("supplier_replacement_unit")
    private String supplierReplacementUnit;

    @TableField("supplier_replacement_unit_price")
    private BigDecimal supplierReplacementUnitPrice;

    @TableField("supplier_replacement_line_amount")
    private BigDecimal supplierReplacementLineAmount;

    @TableField("supplier_proposed_at")
    private LocalDateTime supplierProposedAt;

    @TableField("requester_confirm_user_id")
    private Long requesterConfirmUserId;

    @TableField("requester_confirmed_at")
    private LocalDateTime requesterConfirmedAt;

    @TableField("requester_confirm_remark")
    private String requesterConfirmRemark;

    @TableField("status")
    private String status;

    @TableField("approver_user_id")
    private Long approverUserId;

    @TableField("approved_at")
    private LocalDateTime approvedAt;

    @TableField("approval_remark")
    private String approvalRemark;
}
