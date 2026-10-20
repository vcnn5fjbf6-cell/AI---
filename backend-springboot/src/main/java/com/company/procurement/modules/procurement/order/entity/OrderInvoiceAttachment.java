package com.company.procurement.modules.procurement.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("order_invoice_attachment")
public class OrderInvoiceAttachment extends BaseEntity {

    @TableField("submission_id")
    private Long submissionId;

    @TableField("file_name")
    private String fileName;

    @TableField("file_ext")
    private String fileExt;

    @TableField("content_type")
    private String contentType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_sha256")
    private String fileSha256;

    @TableField("storage_path")
    private String storagePath;
}
