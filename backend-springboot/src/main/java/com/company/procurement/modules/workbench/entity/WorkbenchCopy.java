package com.company.procurement.modules.workbench.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workbench_copy")
public class WorkbenchCopy extends BaseEntity {

    @TableField("user_id")
    private Long userId;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_id")
    private Long sourceId;

    @TableField("copied_by")
    private Long copiedBy;

    @TableField("copied_at")
    private LocalDateTime copiedAt;

    @TableField("remark")
    private String remark;
}
