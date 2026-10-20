package com.company.procurement.modules.workbench.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workbench_notice")
public class WorkbenchNotice extends BaseEntity {

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("pinned")
    private Integer pinned;

    @TableField("status")
    private Integer status;

    @TableField("sort_no")
    private Integer sortNo;
}
