package com.company.procurement.modules.approval.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.entity.ApprovalTask;
import com.company.procurement.modules.approval.record.event.ApprovalTasksCreatedEvent;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalTaskMapper;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApprovalTaskService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_AUTO_CLOSED = "AUTO_CLOSED";
    private static final String APPROVAL_CONFIRM_WORKDAYS_KEY = "approval_confirm_workdays";
    private static final String MONTHLY_AGGREGATE_DAY_KEY = "monthly_aggregate_day";
    private static final int DEFAULT_APPROVAL_STAGE_BUSINESS_DAYS = 4;
    private static final int REPLACEMENT_CONFIRM_DAYS = 2;
    private static final int DEFAULT_MONTHLY_AGGREGATE_DAY = 20;

    private final ApprovalTaskMapper approvalTaskMapper;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final ProcurementSettingMapper settingMapper;
    private final ApplicationEventPublisher eventPublisher;

    public record PendingTaskReassignment(Long taskId, Long departingUserId, Long replacementUserId) {
    }

    @Transactional(rollbackFor = Exception.class)
    public void createTasks(ApprovalInstance instance,
                            PurchaseRequest request,
                            ProcurementApprovalRouteResolver.ApprovalAssignment assignment) {
        if (instance == null || request == null || assignment == null) {
            return;
        }
        if (assignment.candidateUserIds() == null || assignment.candidateUserIds().isEmpty()) {
            throw new BizException("APPROVER_NOT_CONFIGURED", "No approver configured for " + assignment.levelName());
        }
        closePendingTasks(instance.getId(), "NEW_LEVEL_STARTED");
        for (Long userId : assignment.candidateUserIds()) {
            ApprovalTask task = new ApprovalTask();
            task.setInstanceId(instance.getId());
            task.setPurchaseRequestId(request.getId());
            task.setLevelNo(assignment.levelNo());
            task.setLevelCode(assignment.levelCode());
            task.setLevelName(assignment.levelName());
            task.setRoleCode(ProcurementWorkflow.roleCodeForNode(assignment.levelNo()));
            task.setCandidateUserId(userId);
            task.setPassRule(assignment.passRule());
            task.setStatus(STATUS_PENDING);
            task.setAction(null);
            task.setActionComment(null);
            task.setActionAt(null);
            task.setClosedReason(null);
            task.setDueAt(approvalDueAt(request, assignment.levelNo(), assignment.levelCode(), LocalDateTime.now()));
            task.setReminderStage(0);
            task.setSubmitRound(request.getSubmitCount() == null ? 1 : request.getSubmitCount());
            createOrReopenTask(task);
        }
        publishTasksCreated(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createBusinessTask(ApprovalInstance instance,
                                   Long candidateUserId,
                                   Integer levelNo,
                                   String levelCode,
                                   String levelName,
                                   String roleCode,
                                   Integer submitRound,
                                   LocalDateTime dueAt) {
        int normalizedLevel = levelNo == null || levelNo < 1 ? 1 : levelNo;
        ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
            new ProcurementApprovalRouteResolver.ApprovalAssignment(
                candidateUserId,
                candidateUserId == null ? List.of() : List.of(candidateUserId),
                "",
                true,
                null,
                null,
                normalizedLevel,
                StringUtils.hasText(levelCode) ? levelCode.trim() : "BUSINESS_REVIEW",
                StringUtils.hasText(levelName) ? levelName.trim() : "业务审批",
                ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
                true
            );
        createBusinessTasks(instance, assignment, roleCode, submitRound, dueAt);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createBusinessTasks(ApprovalInstance instance,
                                    ProcurementApprovalRouteResolver.ApprovalAssignment assignment,
                                    String roleCode,
                                    Integer submitRound,
                                    LocalDateTime dueAt) {
        if (instance == null || instance.getId() == null || assignment == null
            || assignment.candidateUserIds() == null || assignment.candidateUserIds().isEmpty()) {
            throw new BizException("APPROVER_NOT_CONFIGURED", "Business approval task requires an approver");
        }
        closePendingTasks(instance.getId(), "NEW_LEVEL_STARTED");
        for (Long candidateUserId : new LinkedHashSet<>(assignment.candidateUserIds())) {
            if (candidateUserId == null) {
                continue;
            }
            ApprovalTask task = new ApprovalTask();
            task.setInstanceId(instance.getId());
            task.setPurchaseRequestId(null);
            task.setLevelNo(assignment.levelNo());
            task.setLevelCode(assignment.levelCode());
            task.setLevelName(assignment.levelName());
            task.setRoleCode(StringUtils.hasText(roleCode) ? roleCode.trim() : null);
            task.setCandidateUserId(candidateUserId);
            task.setPassRule(normalizePassRule(assignment.passRule()));
            task.setStatus(STATUS_PENDING);
            task.setDueAt(dueAt);
            task.setSubmitRound(submitRound == null || submitRound < 1 ? 1 : submitRound);
            createOrReopenTask(task);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean completeBusinessTask(ApprovalInstance instance,
                                        AuthUser authUser,
                                        String action,
                                        String comment,
                                        LocalDateTime actionTime) {
        if (instance == null || instance.getId() == null || authUser == null) {
            throw new BizException("APPROVAL_TASK_NOT_FOUND", "Business approval task does not exist");
        }
        Integer levelNo = instance.getCurrentLevelNo() == null
            ? instance.getCurrentNodeNo()
            : instance.getCurrentLevelNo();
        List<ApprovalTask> pendingTasks = pendingTasks(instance.getId(), levelNo);
        ApprovalTask currentTask = resolveCurrentTask(instance, pendingTasks, authUser);
        if (currentTask == null) {
            throw new BizException("FORBIDDEN", "Current account has no pending approval task at this stage");
        }
        String normalizedAction = StringUtils.hasText(action) ? action.trim().toUpperCase(Locale.ROOT) : "";
        if (!List.of("APPROVE", "RETURN").contains(normalizedAction)) {
            throw new BizException("APPROVAL_ACTION_INVALID", "Business approval only supports APPROVE or RETURN");
        }
        boolean approved = "APPROVE".equals(normalizedAction);
        currentTask.setStatus(approved ? STATUS_APPROVED : STATUS_RETURNED);
        currentTask.setAction(normalizedAction);
        currentTask.setActionComment(comment);
        currentTask.setActionAt(actionTime == null ? LocalDateTime.now() : actionTime);
        approvalTaskMapper.updateById(currentTask);
        if (!approved) {
            closePendingTasks(instance.getId(), levelNo, "BUSINESS_LEVEL_RETURN");
            return true;
        }
        List<ApprovalTask> remainingPendingTasks = pendingTasks(instance.getId(), levelNo);
        if (requiresAllApproval(instance, normalizePassRule(currentTask.getPassRule()), remainingPendingTasks)) {
            return remainingPendingTasks.isEmpty();
        }
        closePendingTasks(instance.getId(), levelNo, "BUSINESS_LEVEL_ANY_ONE_APPROVED");
        return true;
    }

    public boolean hasPendingBusinessTask(ApprovalInstance instance, AuthUser authUser) {
        if (instance == null || instance.getId() == null || authUser == null || authUser.getUserId() == null) {
            return false;
        }
        Integer levelNo = instance.getCurrentLevelNo() == null
            ? instance.getCurrentNodeNo()
            : instance.getCurrentLevelNo();
        return pendingTasks(instance.getId(), levelNo).stream()
            .anyMatch(task -> Objects.equals(task.getCandidateUserId(), authUser.getUserId()));
    }

    public List<Long> pendingBusinessInstanceIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .isNull(ApprovalTask::getPurchaseRequestId)
                .eq(ApprovalTask::getCandidateUserId, userId)
                .eq(ApprovalTask::getStatus, STATUS_PENDING)
                .orderByAsc(ApprovalTask::getId))
            .stream()
            .map(ApprovalTask::getInstanceId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean completeTask(ApprovalInstance instance,
                                PurchaseRequest request,
                                AuthUser authUser,
                                String action,
                                String comment,
                                LocalDateTime actionTime) {
        if (instance == null || request == null || authUser == null) {
            return true;
        }
        int levelNo = request.getCurrentNodeNo() == null ? 0 : request.getCurrentNodeNo();
        List<ApprovalTask> pendingTasks = pendingTasks(instance.getId(), levelNo);
        if (pendingTasks.isEmpty()) {
            return true;
        }

        ApprovalTask currentTask = resolveCurrentTask(instance, pendingTasks, authUser);
        if (currentTask == null) {
            throw new BizException("FORBIDDEN", "Current account has no pending approval task at this stage");
        }

        String normalizedAction = StringUtils.hasText(action) ? action.trim().toUpperCase() : "";
        String nextTaskStatus = switch (normalizedAction) {
            case ProcurementWorkflow.ACTION_APPROVE -> STATUS_APPROVED;
            case ProcurementWorkflow.ACTION_REJECT -> STATUS_REJECTED;
            case ProcurementWorkflow.ACTION_RETURN -> STATUS_RETURNED;
            default -> STATUS_AUTO_CLOSED;
        };
        currentTask.setStatus(nextTaskStatus);
        currentTask.setAction(normalizedAction);
        currentTask.setActionComment(comment);
        currentTask.setActionAt(actionTime);
        approvalTaskMapper.updateById(currentTask);

        if (!ProcurementWorkflow.ACTION_APPROVE.equals(normalizedAction)) {
            closePendingTasks(instance.getId(), levelNo, "LEVEL_" + normalizedAction);
            return true;
        }

        String passRule = normalizePassRule(currentTask.getPassRule());
        List<ApprovalTask> remainingPendingTasks = pendingTasks(instance.getId(), levelNo);
        if (requiresAllApproval(instance, passRule, remainingPendingTasks)) {
            return remainingPendingTasks.isEmpty();
        }
        closePendingTasks(instance.getId(), levelNo, "ANY_ONE_APPROVED");
        return true;
    }

    public Long resolveActionCandidateUserId(ApprovalInstance instance, PurchaseRequest request, AuthUser authUser) {
        if (request == null || authUser == null) {
            return authUser == null ? null : authUser.getUserId();
        }
        if (instance == null || instance.getId() == null) {
            return authUser.getUserId();
        }
        int levelNo = request.getCurrentNodeNo() == null ? 0 : request.getCurrentNodeNo();
        ApprovalTask currentTask = resolveCurrentTask(instance, pendingTasks(instance.getId(), levelNo), authUser);
        return currentTask == null ? authUser.getUserId() : currentTask.getCandidateUserId();
    }

    private ApprovalTask resolveCurrentTask(ApprovalInstance instance, List<ApprovalTask> pendingTasks, AuthUser authUser) {
        if (pendingTasks == null || pendingTasks.isEmpty() || authUser == null) {
            return null;
        }
        ApprovalTask userTask = pendingTasks.stream()
            .filter(task -> Objects.equals(task.getCandidateUserId(), authUser.getUserId()))
            .findFirst()
            .orElse(null);
        if (userTask != null) {
            return userTask;
        }
        if (!DataScopeHelper.isSystemAdmin(authUser)) {
            return null;
        }
        Long currentApproverId = instance == null ? null : instance.getCurrentApproverId();
        if (currentApproverId != null) {
            ApprovalTask currentApproverTask = pendingTasks.stream()
                .filter(task -> Objects.equals(task.getCandidateUserId(), currentApproverId))
                .findFirst()
                .orElse(null);
            if (currentApproverTask != null) {
                return currentApproverTask;
            }
        }
        return pendingTasks.get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public void closePendingTasks(Long instanceId, String reason) {
        closePendingTasks(instanceId, null, reason);
    }

    public boolean canActOnCurrentTask(ApprovalInstance instance, PurchaseRequest request, AuthUser authUser) {
        if (instance == null || request == null || authUser == null) {
            return true;
        }
        int levelNo = request.getCurrentNodeNo() == null ? 0 : request.getCurrentNodeNo();
        List<ApprovalTask> pendingTasks = pendingTasks(instance.getId(), levelNo);
        if (pendingTasks.isEmpty()) {
            return true;
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        return pendingTasks.stream()
            .anyMatch(task -> Objects.equals(task.getCandidateUserId(), authUser.getUserId()));
    }

    public boolean hasPendingTaskForUser(ApprovalInstance instance, PurchaseRequest request, AuthUser authUser) {
        if (instance == null || request == null || authUser == null || authUser.getUserId() == null) {
            return false;
        }
        int levelNo = request.getCurrentNodeNo() == null ? 0 : request.getCurrentNodeNo();
        return pendingTasks(instance.getId(), levelNo).stream()
            .anyMatch(task -> Objects.equals(task.getCandidateUserId(), authUser.getUserId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void addSignTask(ApprovalInstance instance,
                            PurchaseRequest request,
                            Long addSignUserId,
                            Long initiatorUserId) {
        if (instance == null || request == null || addSignUserId == null) {
            throw new BizException("ADD_SIGN_TARGET_INVALID", "Add-sign target is required");
        }
        int levelNo = request.getCurrentNodeNo() == null ? 0 : request.getCurrentNodeNo();
        List<ApprovalTask> pendingTasks = pendingTasks(instance.getId(), levelNo);
        if (pendingTasks.isEmpty()) {
            throw new BizException("APPROVAL_TASK_NOT_FOUND", "Current stage has no pending approval task");
        }
        boolean alreadyPending = pendingTasks.stream()
            .anyMatch(task -> Objects.equals(task.getCandidateUserId(), addSignUserId));
        if (alreadyPending) {
            throw new BizException("ADD_SIGN_TARGET_DUPLICATED", "The add-sign user already has a pending task");
        }
        boolean originalRequiresAll = ProcurementApprovalRouteResolver.PASS_RULE_ALL.equals(normalizePassRule(instance.getCurrentPassRule()))
            || pendingTasks.stream()
                .anyMatch(task -> ProcurementApprovalRouteResolver.PASS_RULE_ALL.equals(normalizePassRule(task.getPassRule())));
        String passRule = ProcurementApprovalRouteResolver.PASS_RULE_ALL;
        instance.setCurrentPassRule(passRule);
        if (originalRequiresAll) {
            approvalTaskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instance.getId())
                .eq(ApprovalTask::getLevelNo, levelNo)
                .eq(ApprovalTask::getStatus, STATUS_PENDING)
                .set(ApprovalTask::getPassRule, passRule));
        } else {
            Long requiredApproverId = pendingTasks.stream()
                .filter(task -> Objects.equals(task.getCandidateUserId(), initiatorUserId))
                .map(ApprovalTask::getCandidateUserId)
                .findFirst()
                .orElse(pendingTasks.get(0).getCandidateUserId());
            approvalTaskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instance.getId())
                .eq(ApprovalTask::getLevelNo, levelNo)
                .eq(ApprovalTask::getStatus, STATUS_PENDING)
                .eq(ApprovalTask::getCandidateUserId, requiredApproverId)
                .set(ApprovalTask::getPassRule, passRule));
            approvalTaskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instance.getId())
                .eq(ApprovalTask::getLevelNo, levelNo)
                .eq(ApprovalTask::getStatus, STATUS_PENDING)
                .ne(ApprovalTask::getCandidateUserId, requiredApproverId)
                .set(ApprovalTask::getStatus, STATUS_AUTO_CLOSED)
                .set(ApprovalTask::getClosedReason, "ADD_SIGN_PEER_CLOSED"));
        }

        ApprovalTask task = new ApprovalTask();
        task.setInstanceId(instance.getId());
        task.setPurchaseRequestId(request.getId());
        task.setLevelNo(levelNo);
        task.setLevelCode(instance.getCurrentLevelCode());
        task.setLevelName(ProcurementWorkflow.nodeNameForNode(levelNo));
        task.setRoleCode(ProcurementWorkflow.roleCodeForNode(levelNo));
        task.setCandidateUserId(addSignUserId);
        task.setPassRule(passRule);
        task.setStatus(STATUS_PENDING);
        task.setDueAt(approvalDueAt(request, levelNo, instance.getCurrentLevelCode(), LocalDateTime.now()));
        task.setReminderStage(0);
        task.setSubmitRound(request.getSubmitCount() == null ? 1 : request.getSubmitCount());
        task.setClosedReason("ADD_SIGN");
        createOrReopenTask(task);
        publishTasksCreated(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reassignPendingTasks(List<PendingTaskReassignment> reassignments) {
        if (reassignments == null || reassignments.isEmpty()) {
            return;
        }
        Set<Long> changedRequestIds = new LinkedHashSet<>();
        for (PendingTaskReassignment reassignment : reassignments) {
            ApprovalTask task = approvalTaskMapper.selectById(reassignment.taskId());
            if (task == null
                || !STATUS_PENDING.equals(task.getStatus())
                || !Objects.equals(task.getCandidateUserId(), reassignment.departingUserId())) {
                throw new BizException("USER_OFFBOARDING_TASK_STALE", "待办审批已变化，请刷新后重新办理离职");
            }

            ApprovalTask existing = approvalTaskMapper.selectOne(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, task.getInstanceId())
                .eq(ApprovalTask::getLevelNo, task.getLevelNo())
                .eq(ApprovalTask::getCandidateUserId, reassignment.replacementUserId())
                .eq(ApprovalTask::getSubmitRound, task.getSubmitRound())
                .ne(ApprovalTask::getId, task.getId())
                .last("LIMIT 1"));
            if (existing != null) {
                if (!STATUS_PENDING.equals(existing.getStatus())) {
                    throw new BizException(
                        "USER_OFFBOARDING_TASK_REPLACEMENT_CONFLICT",
                        "所选替换人员已处理过该审批，请为此项选择其他人员"
                    );
                }
                task.setStatus(STATUS_AUTO_CLOSED);
                task.setClosedReason("USER_OFFBOARDING_REASSIGNED_TO_EXISTING");
            } else {
                task.setCandidateUserId(reassignment.replacementUserId());
                task.setClosedReason("USER_OFFBOARDING_REASSIGNED");
            }
            approvalTaskMapper.updateById(task);

            ApprovalInstance instance = approvalInstanceMapper.selectById(task.getInstanceId());
            if (instance != null && Objects.equals(instance.getCurrentApproverId(), reassignment.departingUserId())) {
                instance.setCurrentApproverId(reassignment.replacementUserId());
                approvalInstanceMapper.updateById(instance);
            }
            if (task.getPurchaseRequestId() != null) {
                changedRequestIds.add(task.getPurchaseRequestId());
            }
        }
        changedRequestIds.forEach(requestId ->
            eventPublisher.publishEvent(new ApprovalTasksCreatedEvent(requestId)));
    }

    public List<ApprovalTask> pendingTimeoutTasks(LocalDateTime now, boolean force) {
        LambdaQueryWrapper<ApprovalTask> wrapper = new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getStatus, STATUS_PENDING)
            .orderByAsc(ApprovalTask::getDueAt)
            .orderByAsc(ApprovalTask::getId);
        if (!force) {
            wrapper.isNotNull(ApprovalTask::getDueAt)
                .le(ApprovalTask::getDueAt, now);
        }
        return approvalTaskMapper.selectList(wrapper);
    }

    public boolean hasOverdueApprovedTask(Long purchaseRequestId) {
        if (purchaseRequestId == null) {
            return false;
        }
        return approvalTaskMapper.selectCount(new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getPurchaseRequestId, purchaseRequestId)
            .eq(ApprovalTask::getStatus, STATUS_APPROVED)
            .isNotNull(ApprovalTask::getDueAt)
            .isNotNull(ApprovalTask::getActionAt)
            .apply("action_at > due_at")) > 0;
    }

    public LocalDateTime currentDueAt(Long purchaseRequestId, Integer levelNo) {
        if (purchaseRequestId == null) {
            return null;
        }
        LambdaQueryWrapper<ApprovalTask> wrapper = new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getPurchaseRequestId, purchaseRequestId)
            .eq(ApprovalTask::getStatus, STATUS_PENDING)
            .isNotNull(ApprovalTask::getDueAt)
            .orderByAsc(ApprovalTask::getDueAt)
            .orderByAsc(ApprovalTask::getId);
        if (levelNo != null && levelNo > 0) {
            wrapper.eq(ApprovalTask::getLevelNo, levelNo);
        }
        wrapper.last("LIMIT 1");
        ApprovalTask task = approvalTaskMapper.selectOne(wrapper);
        return task == null ? null : task.getDueAt();
    }

    private void createOrReopenTask(ApprovalTask task) {
        Integer submitRound = task.getSubmitRound() == null ? 1 : task.getSubmitRound();
        ApprovalTask existing = approvalTaskMapper.selectOne(new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getInstanceId, task.getInstanceId())
            .eq(ApprovalTask::getLevelNo, task.getLevelNo())
            .eq(ApprovalTask::getCandidateUserId, task.getCandidateUserId())
            .eq(ApprovalTask::getSubmitRound, submitRound)
            .last("LIMIT 1"));
        if (existing == null) {
            task.setSubmitRound(submitRound);
            approvalTaskMapper.insert(task);
            return;
        }

        approvalTaskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
            .eq(ApprovalTask::getId, existing.getId())
            .set(ApprovalTask::getPurchaseRequestId, task.getPurchaseRequestId())
            .set(ApprovalTask::getLevelCode, task.getLevelCode())
            .set(ApprovalTask::getLevelName, task.getLevelName())
            .set(ApprovalTask::getRoleCode, task.getRoleCode())
            .set(ApprovalTask::getPassRule, task.getPassRule())
            .set(ApprovalTask::getStatus, STATUS_PENDING)
            .set(ApprovalTask::getAction, null)
            .set(ApprovalTask::getActionComment, null)
            .set(ApprovalTask::getActionAt, null)
            .set(ApprovalTask::getClosedReason, task.getClosedReason())
            .set(ApprovalTask::getDueAt, task.getDueAt())
            .set(ApprovalTask::getReminderStage, task.getReminderStage() == null ? 0 : task.getReminderStage())
            .set(ApprovalTask::getSubmitRound, submitRound)
            .set(ApprovalTask::getUpdatedAt, LocalDateTime.now()));
    }

    private LocalDateTime approvalDueAt(PurchaseRequest request,
                                        Integer levelNo,
                                        String levelCode,
                                        LocalDateTime startAt) {
        if (ProcurementWorkflow.LEVEL_CODE_OVER_PRICE_REVIEW.equals(levelCode)) {
            return (startAt == null ? LocalDateTime.now() : startAt).plusHours(24);
        }
        if (Objects.equals(levelNo, ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW)) {
            return (startAt == null ? LocalDateTime.now() : startAt).plusDays(REPLACEMENT_CONFIRM_DAYS);
        }
        if (isRegularFloorLeaderReview(request, levelNo)) {
            return floorLeaderMonthlyCutoffDueAt(startAt);
        }
        return approvalBusinessDueAt(startAt);
    }

    private boolean isRegularFloorLeaderReview(PurchaseRequest request, Integer levelNo) {
        if (!Objects.equals(levelNo, ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW)) {
            return false;
        }
        Integer urgencyLevel = request == null ? null : request.getUrgencyLevel();
        return (urgencyLevel == null || urgencyLevel < 2) && !isMonthlyAggregateRequest(request);
    }

    private boolean isMonthlyAggregateRequest(PurchaseRequest request) {
        if (request == null || !StringUtils.hasText(request.getReqNo())) {
            return false;
        }
        return request.getReqNo().trim().toUpperCase(Locale.ROOT).startsWith("HZCG-");
    }

    private LocalDateTime floorLeaderMonthlyCutoffDueAt(LocalDateTime startAt) {
        LocalDate baseDate = (startAt == null ? LocalDateTime.now() : startAt).toLocalDate();
        LocalDate cutoffDate = cutoffDateInMonth(baseDate);
        LocalDateTime cutoffAt = cutoffDate.atTime(LocalTime.of(23, 59, 59));
        LocalDateTime start = startAt == null ? LocalDateTime.now() : startAt;
        if (cutoffAt.isBefore(start)) {
            cutoffAt = cutoffDateInMonth(baseDate.plusMonths(1)).atTime(LocalTime.of(23, 59, 59));
        }
        return cutoffAt;
    }

    private LocalDate cutoffDateInMonth(LocalDate date) {
        int cutoffDay = monthlyAggregateDay();
        return date.withDayOfMonth(Math.min(cutoffDay, date.lengthOfMonth()));
    }

    private LocalDateTime approvalBusinessDueAt(LocalDateTime startAt) {
        LocalDateTime result = startAt == null ? LocalDateTime.now() : startAt;
        int targetDays = approvalStageBusinessDays();
        int added = 0;
        while (added < targetDays) {
            result = result.plusDays(1);
            DayOfWeek dayOfWeek = result.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return result;
    }

    public LocalDateTime businessApprovalDueAt(LocalDateTime startAt) {
        return approvalBusinessDueAt(startAt);
    }

    private int monthlyAggregateDay() {
        return intSetting(MONTHLY_AGGREGATE_DAY_KEY, DEFAULT_MONTHLY_AGGREGATE_DAY, 1, 31);
    }

    private int approvalStageBusinessDays() {
        return intSetting(APPROVAL_CONFIRM_WORKDAYS_KEY, DEFAULT_APPROVAL_STAGE_BUSINESS_DAYS, 1, 30);
    }

    private int intSetting(String key, int defaultValue, int min, int max) {
        ProcurementSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, key)
            .eq(ProcurementSetting::getIsDeleted, 0)
            .last("LIMIT 1"));
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(setting.getSettingValue().trim());
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String normalizePassRule(String passRule) {
        if (!StringUtils.hasText(passRule)) {
            return ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE;
        }
        return passRule.trim().toUpperCase();
    }

    private boolean requiresAllApproval(ApprovalInstance instance,
                                        String currentTaskPassRule,
                                        List<ApprovalTask> remainingPendingTasks) {
        if (ProcurementApprovalRouteResolver.PASS_RULE_ALL.equals(currentTaskPassRule)) {
            return true;
        }
        if (instance != null
            && ProcurementApprovalRouteResolver.PASS_RULE_ALL.equals(normalizePassRule(instance.getCurrentPassRule()))) {
            return true;
        }
        return remainingPendingTasks.stream().anyMatch(task ->
            ProcurementApprovalRouteResolver.PASS_RULE_ALL.equals(normalizePassRule(task.getPassRule()))
                || "ADD_SIGN".equalsIgnoreCase(task.getClosedReason()));
    }

    private List<ApprovalTask> pendingTasks(Long instanceId, Integer levelNo) {
        LambdaQueryWrapper<ApprovalTask> wrapper = new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getInstanceId, instanceId)
            .eq(ApprovalTask::getStatus, STATUS_PENDING)
            .orderByAsc(ApprovalTask::getId);
        if (levelNo != null && levelNo > 0) {
            wrapper.eq(ApprovalTask::getLevelNo, levelNo);
        }
        return approvalTaskMapper.selectList(wrapper);
    }

    private void publishTasksCreated(PurchaseRequest request) {
        eventPublisher.publishEvent(new ApprovalTasksCreatedEvent(request == null ? null : request.getId()));
    }

    private void closePendingTasks(Long instanceId, Integer levelNo, String reason) {
        if (instanceId == null) {
            return;
        }
        LambdaUpdateWrapper<ApprovalTask> wrapper = new LambdaUpdateWrapper<ApprovalTask>()
            .eq(ApprovalTask::getInstanceId, instanceId)
            .eq(ApprovalTask::getStatus, STATUS_PENDING)
            .set(ApprovalTask::getStatus, STATUS_AUTO_CLOSED)
            .set(ApprovalTask::getClosedReason, reason);
        if (levelNo != null && levelNo > 0) {
            wrapper.eq(ApprovalTask::getLevelNo, levelNo);
        }
        approvalTaskMapper.update(null, wrapper);
    }
}
