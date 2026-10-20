package com.company.procurement.modules.ehs.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ehs_registry_record")
public class EhsRegistryRecord extends BaseEntity {

    @TableField("record_no")
    private String recordNo;

    @TableField("module_type")
    private String moduleType;

    @TableField("title")
    private String title;

    @TableField("category")
    private String category;

    @TableField("sub_type")
    private String subType;

    @TableField("building")
    private String building;

    @TableField("location")
    private String location;

    @TableField("risk_level")
    private String riskLevel;

    @TableField("status")
    private String status;

    @TableField("owner_user_id")
    private Long ownerUserId;

    @TableField("owner_name")
    private String ownerName;

    @TableField("handler_name")
    private String handlerName;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("unit")
    private String unit;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField("deadline_at")
    private LocalDateTime deadlineAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("detail_json")
    private String detailJson;

    @TableField("remark")
    private String remark;
}
