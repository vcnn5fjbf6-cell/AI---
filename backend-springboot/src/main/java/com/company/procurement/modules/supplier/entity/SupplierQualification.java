package com.company.procurement.modules.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("supplier_qualification")
public class SupplierQualification extends BaseEntity {

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("qualification_type")
    private String qualificationType;

    @TableField("qualification_no")
    private String qualificationNo;

    @TableField("issuing_org")
    private String issuingOrg;

    @TableField("valid_from")
    private LocalDate validFrom;

    @TableField("valid_to")
    private LocalDate validTo;

    @TableField("file_url")
    private String fileUrl;

    @TableField("verify_status")
    private String verifyStatus;

    @TableField("status")
    private String status;

    @TableField("verify_comment")
    private String verifyComment;
}
