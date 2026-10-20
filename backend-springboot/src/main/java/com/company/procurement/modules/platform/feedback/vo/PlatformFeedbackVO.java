package com.company.procurement.modules.platform.feedback.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PlatformFeedbackVO {

    private Long id;
    private String feedbackNo;
    private String category;
    private String title;
    private String content;
    private String links;
    private String pageUrl;
    private Long submitterUserId;
    private String submitterUsername;
    private String submitterRealName;
    private String submitterUserType;
    private String submitterDeptName;
    private Long submitterSupplierId;
    private Integer attachmentCount;
    private String status;
    private Long handlerUserId;
    private String handlerName;
    private String handleComment;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PlatformFeedbackAttachmentVO> attachments = new ArrayList<>();
}
