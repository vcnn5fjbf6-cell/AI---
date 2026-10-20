package com.company.procurement.modules.org.policy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("procurement_approval_level_user")
public class ProcurementApprovalLevelUser extends BaseEntity {

    @TableField("level_id")
    private Long levelId;

    @TableField("user_id")
    private Long userId;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("effective_from")
    private LocalDateTime effectiveFrom;

    @TableField("effective_to")
    private LocalDateTime effectiveTo;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;
}
