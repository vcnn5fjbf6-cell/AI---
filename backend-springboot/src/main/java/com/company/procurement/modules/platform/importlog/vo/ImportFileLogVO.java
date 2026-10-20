package com.company.procurement.modules.platform.importlog.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImportFileLogVO {

    private Long id;
    private String sourceChannel;
    private String importType;
    private String importTypeName;
    private String fileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String fileSha256;
    private String storageMode;
    private String storagePath;
    private Integer rowCount;
    private Integer successCount;
    private Integer failureCount;
    private String processStatus;
    private Long uploaderUserId;
    private String uploaderUsername;
    private String uploaderRealName;
    private String sourceUserKey;
    private String messageId;
    private Long messageLogId;
    private String relatedBizType;
    private Long relatedBizId;
    private String relatedBizNo;
    private String resultSummary;
    private String errorMessage;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
