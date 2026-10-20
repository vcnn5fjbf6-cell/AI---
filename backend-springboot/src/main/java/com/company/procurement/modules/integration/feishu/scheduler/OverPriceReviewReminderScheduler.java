package com.company.procurement.modules.integration.feishu.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.procurement.modules.approval.record.entity.ApprovalTask;
import com.company.procurement.modules.approval.record.mapper.ApprovalTaskMapper;
import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OverPriceReviewReminderScheduler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApprovalTaskMapper approvalTaskMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final SysUserMapper userMapper;
    private final FeishuApprovalCardService approvalCardService;

    @Scheduled(fixedDelayString = "${procurement.over-price-review.reminder-fixed-delay-ms:600000}")
    public void remindPendingReviews() {
        LocalDateTime now = LocalDateTime.now();
        List<ApprovalTask> tasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getStatus, "PENDING")
            .eq(ApprovalTask::getLevelCode, ProcurementWorkflow.LEVEL_CODE_OVER_PRICE_REVIEW)
            .isNotNull(ApprovalTask::getDueAt)
            .orderByAsc(ApprovalTask::getDueAt)
            .orderByAsc(ApprovalTask::getId));
        for (ApprovalTask task : tasks) {
            try {
                remind(task, now);
            } catch (RuntimeException ex) {
                log.warn("Over-price review reminder failed, taskId={}", task.getId(), ex);
            }
        }
    }

    private void remind(ApprovalTask task, LocalDateTime now) {
        int stage = reminderStage(now, task.getDueAt());
        if (stage == 0 || stage <= valueOrZero(task.getReminderStage())) {
            return;
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(task.getPurchaseRequestId());
        SysUser applicant = request == null || request.getApplicantUserId() == null
            ? null
            : userMapper.selectById(request.getApplicantUserId());
        FeishuApprovalCardActionResult result = approvalCardService.sendBusinessNotification(
            task.getCandidateUserId(),
            reminderTitle(stage),
            reminderContent(request, applicant, task.getDueAt(), stage)
        );
        if (result == null || !result.isSuccess()) {
            return;
        }
        approvalTaskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
            .eq(ApprovalTask::getId, task.getId())
            .eq(ApprovalTask::getStatus, "PENDING")
            .lt(ApprovalTask::getReminderStage, stage)
            .set(ApprovalTask::getReminderStage, stage));
    }

    static int reminderStage(LocalDateTime now, LocalDateTime dueAt) {
        if (now == null || dueAt == null || now.isBefore(dueAt.minusHours(12))) {
            return 0;
        }
        if (!now.isBefore(dueAt)) {
            return 3;
        }
        if (!now.isBefore(dueAt.minusHours(2))) {
            return 2;
        }
        return 1;
    }

    private String reminderTitle(int stage) {
        return switch (stage) {
            case 1 -> "超价复核剩余 12 小时";
            case 2 -> "超价复核剩余 2 小时";
            default -> "超价复核已逾期";
        };
    }

    private String reminderContent(PurchaseRequest request,
                                   SysUser applicant,
                                   LocalDateTime dueAt,
                                   int stage) {
        String requestNo = request == null ? "-" : text(request.getReqNo());
        String title = request == null ? "-" : text(request.getReqTitle());
        String applicantName = applicant == null
            ? "-"
            : text(StringUtils.hasText(applicant.getRealName()) ? applicant.getRealName() : applicant.getUsername());
        String action = stage == 3 ? "已超过复核时限，请立即处理。" : "请在截止时间前完成通过或单项驳回。";
        return "采购申请：" + requestNo + " / " + title
            + "\n提交者：" + applicantName
            + "\n截止时间：" + dueAt.format(DATE_TIME_FORMATTER)
            + "\n" + action;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }
}
