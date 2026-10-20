package com.company.procurement.modules.platform.importlog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_import_file_log")
public class ImportFileLog extends BaseEntity {

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("import_type")
    private String importType;

    @TableField("import_type_name")
    private String importTypeName;

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

    @TableField("storage_mode")
    private String storageMode;

    @TableField("storage_path")
    private String storagePath;

    @TableField("row_count")
    private Integer rowCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("failure_count")
    private Integer failureCount;

    @TableField("process_status")
    private String processStatus;

    @TableField("uploader_user_id")
    private Long uploaderUserId;

    @TableField("uploader_username")
    private String uploaderUsername;

    @TableField("uploader_real_name")
    private String uploaderRealName;

    @TableField("source_user_key")
    private String sourceUserKey;

    @TableField("message_id")
    private String messageId;

    @TableField("message_log_id")
    private Long messageLogId;

    @TableField("related_biz_type")
    private String relatedBizType;

    @TableField("related_biz_id")
    private Long relatedBizId;

    @TableField("related_biz_no")
    private String relatedBizNo;

    @TableField("result_summary")
    private String resultSummary;

    @TableField("error_message")
    private String errorMessage;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}
