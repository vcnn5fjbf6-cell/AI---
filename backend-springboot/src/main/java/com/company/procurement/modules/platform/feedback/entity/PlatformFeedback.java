package com.company.procurement.modules.platform.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_feedback")
public class PlatformFeedback extends BaseEntity {

    @TableField("feedback_no")
    private String feedbackNo;

    @TableField("category")
    private String category;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("links")
    private String links;

    @TableField("page_url")
    private String pageUrl;

    @TableField("submitter_user_id")
    private Long submitterUserId;

    @TableField("submitter_username")
    private String submitterUsername;

    @TableField("submitter_real_name")
    private String submitterRealName;

    @TableField("submitter_user_type")
    private String submitterUserType;

    @TableField("submitter_dept_name")
    private String submitterDeptName;

    @TableField("submitter_supplier_id")
    private Long submitterSupplierId;

    @TableField("attachment_count")
    private Integer attachmentCount;

    @TableField("status")
    private String status;

    @TableField("handler_user_id")
    private Long handlerUserId;

    @TableField("handler_name")
    private String handlerName;

    @TableField("handle_comment")
    private String handleComment;

    @TableField("handled_at")
    private LocalDateTime handledAt;
}
