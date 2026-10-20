package com.company.procurement.modules.ehs.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("ehs_knowledge_document")
public class EhsKnowledgeDocument extends BaseEntity {

    @TableField("title")
    private String title;
    @TableField("category")
    private String category;
    @TableField("file_name")
    private String fileName;
    @TableField("file_type")
    private String fileType;
    @TableField("content")
    private String content;
    @TableField("status")
    private String status;
    @TableField("uploaded_by")
    private Long uploadedBy;
    @TableField("uploaded_by_name")
    private String uploadedByName;
}
