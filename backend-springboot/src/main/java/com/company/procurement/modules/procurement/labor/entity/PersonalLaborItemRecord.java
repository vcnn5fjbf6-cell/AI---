package com.company.procurement.modules.procurement.labor.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("personal_labor_item_record")
public class PersonalLaborItemRecord extends BaseEntity {

    @TableField("user_id")
    private Long userId;

    @TableField("username")
    private String username;

    @TableField("real_name")
    private String realName;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("item_code")
    private String itemCode;

    @TableField("item_name")
    private String itemName;

    @TableField("spec_model")
    private String specModel;

    @TableField("unit")
    private String unit;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("last_received_date")
    private LocalDate lastReceivedDate;

    @TableField("next_due_date")
    private LocalDate nextDueDate;

    @TableField("status")
    private String status;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("purchase_request_no")
    private String purchaseRequestNo;

    @TableField("purchase_request_item_id")
    private Long purchaseRequestItemId;

    @TableField("emergency_flag")
    private Integer emergencyFlag;

    @TableField("remark")
    private String remark;
}
