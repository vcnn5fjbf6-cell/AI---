package com.company.procurement.modules.platform.feedback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlatformFeedbackAttachmentVO {

    private Long id;
    private Long feedbackId;
    private String fileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String fileSha256;
    private String downloadUrl;
    private LocalDateTime createdAt;
}
