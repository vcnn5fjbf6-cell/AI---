package com.company.procurement.modules.ehs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;
import com.company.procurement.modules.ehs.dto.EhsPermitApprovalDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitCloseDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitQueryDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitSampleDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitSaveDTO;
import com.company.procurement.modules.ehs.dto.EhsPublicPermitSaveDTO;
import com.company.procurement.modules.ehs.entity.EhsPermit;
import com.company.procurement.modules.ehs.entity.EhsPublicAccessToken;
import com.company.procurement.modules.ehs.mapper.EhsPermitMapper;
import com.company.procurement.modules.ehs.vo.EhsPermitVO;
import com.company.procurement.modules.ehs.vo.EhsPublicApprovalRecordVO;
import com.company.procurement.modules.ehs.vo.EhsPublicPermitCreatedVO;
import com.company.procurement.modules.ehs.vo.EhsPublicPermitVO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EhsPermitService {

    public static final String TYPE_WORK_PERMIT = "WORK_PERMIT";
    public static final Set<String> SPECIAL_TYPES = Set.of(
        "HOT_WORK", "HEIGHT_WORK", "CONFINED_SPACE", "ELECTRIC_WORK", "LIFTING_WORK", "EXCAVATION_WORK"
    );
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ApprovalStep>> APPROVAL_STEP_LIST = new TypeReference<>() {
    };

    private final EhsPermitMapper permitMapper;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ApprovalTaskService approvalTaskService;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final EhsNotificationService notificationService;
    private final ProcurementApprovalRouteResolver approvalRouteResolver;
    private final EhsDepartmentOptionService departmentOptionService;
    private final EhsPublicAccessService publicAccessService;
    private final ObjectMapper objectMapper;

    public PageResult<EhsPermitVO> page(EhsPermitQueryDTO query) {
        AuthUser authUser = AuthContextHolder.requireUser();
        LambdaQueryWrapper<EhsPermit> wrapper = new LambdaQueryWrapper<EhsPermit>()
            .orderByDesc(EhsPermit::getUpdatedAt)
            .orderByDesc(EhsPermit::getId);
        if (Boolean.TRUE.equals(query.getPendingMine())) {
            List<Long> instanceIds = approvalTaskService.pendingBusinessInstanceIds(authUser.getUserId());
            if (instanceIds.isEmpty()) {
                wrapper.eq(EhsPermit::getId, -1L);
            } else {
                wrapper.in(EhsPermit::getApprovalInstanceId, instanceIds)
                    .eq(EhsPermit::getApprovalStatus, "APPROVING");
            }
        } else if (StringUtils.hasText(query.getPermitType())) {
            wrapper.eq(EhsPermit::getPermitType, normalizePermitType(query.getPermitType()));
        } else {
            wrapper.isNull(EhsPermit::getParentId);
        }
        if (Boolean.TRUE.equals(query.getMineOnly())) {
            wrapper.eq(EhsPermit::getApplicantUserId, authUser.getUserId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(EhsPermit::getStatus, normalizeCode(query.getStatus()));
        }
        if (StringUtils.hasText(query.getApprovalStatus())) {
            wrapper.eq(EhsPermit::getApprovalStatus, normalizeCode(query.getApprovalStatus()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item.like(EhsPermit::getPermitNo, keyword)
                .or().like(EhsPermit::getTitle, keyword)
                .or().like(EhsPermit::getLocation, keyword)
                .or().like(EhsPermit::getApplicantName, keyword)
                .or().like(EhsPermit::getContractorName, keyword));
        }
        Page<EhsPermit> page = permitMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream()
            .map(permit -> toVO(permit, authUser, false))
            .toList());
    }

    public EhsPermitVO detail(Long id) {
        AuthUser authUser = AuthContextHolder.requireUser();
        return toVO(requirePermit(id), authUser, true);
    }

    public Map<String, Long> stats() {
        List<EhsPermit> parents = permitMapper.selectList(new LambdaQueryWrapper<EhsPermit>()
            .isNull(EhsPermit::getParentId));
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", (long) parents.size());
        stats.put("approving", parents.stream().filter(item -> Set.of("APPROVING", "PENDING_SUB_PERMITS").contains(item.getApprovalStatus())
            || Set.of("PENDING_APPROVAL", "PENDING_SUB_PERMITS").contains(item.getStatus())).count());
        stats.put("approved", parents.stream().filter(item -> Set.of("APPROVED", "IN_PROGRESS", "PENDING_CLOSE").contains(item.getStatus())).count());
        stats.put("closed", parents.stream().filter(item -> "CLOSED".equals(item.getStatus())).count());
        stats.put("returned", parents.stream().filter(item -> "RETURNED".equals(item.getStatus())).count());
        return stats;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(EhsPermitSaveDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        validateDates(dto);
        EhsPermit permit = new EhsPermit();
        permit.setPermitNo(nextPermitNo(TYPE_WORK_PERMIT));
        permit.setPermitType(TYPE_WORK_PERMIT);
        permit.setApplicantUserId(authUser.getUserId());
        permit.setApplicantName(displayName(authUser));
        permit.setSourceType("INTERNAL");
        permit.setStatus("DRAFT");
        permit.setApprovalStatus("DRAFT");
        permit.setSubmitRound(0);
        apply(permit, dto, true);
        permitMapper.insert(permit);
        return permit.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public EhsPublicPermitCreatedVO createPublic(EhsPublicPermitSaveDTO dto) {
        if (StringUtils.hasText(dto.getWebsite())) {
            throw new BizException("EHS_PUBLIC_REQUEST_INVALID", "申请内容校验失败");
        }
        EhsPermitSaveDTO save = toInternalSave(dto);
        validateDates(save);
        EhsPermit permit = new EhsPermit();
        permit.setPermitNo(nextPermitNo(TYPE_WORK_PERMIT));
        permit.setPermitType(TYPE_WORK_PERMIT);
        permit.setApplicantUserId(null);
        permit.setApplicantName(limit(dto.getApplicantName(), 128));
        permit.setApplicantPhone(limit(dto.getApplicantPhone(), 32));
        permit.setApplicantCompany(limit(dto.getApplicantCompany(), 255));
        permit.setSourceType("PUBLIC");
        permit.setStatus("DRAFT");
        permit.setApprovalStatus("DRAFT");
        permit.setSubmitRound(0);
        apply(permit, save, true);
        permitMapper.insert(permit);
        EhsPublicAccessService.IssuedToken access = publicAccessService.issueApplicantToken(permit.getId());
        submitPermit(permit, null);
        return EhsPublicPermitCreatedVO.builder()
            .permitId(permit.getId())
            .permitNo(permit.getPermitNo())
            .accessToken(access.rawToken())
            .accessExpiresAt(access.expiresAt())
            .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EhsPermitSaveDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        EhsPermit permit = requirePermit(id);
        ensureApplicantOrAdmin(permit, authUser);
        if (!Set.of("DRAFT", "RETURNED").contains(permit.getStatus())) {
            throw new BizException("EHS_PERMIT_NOT_EDITABLE", "当前状态的作业票不能修改");
        }
        validateDates(dto);
        apply(permit, dto, permit.getParentId() == null);
        permitMapper.updateById(permit);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AuthUser authUser = AuthContextHolder.requireUser();
        EhsPermit permit = requirePermit(id);
        ensureApplicantOrAdmin(permit, authUser);
        if (!"DRAFT".equals(permit.getStatus())) {
            throw new BizException("EHS_PERMIT_DELETE_FORBIDDEN", "只有草稿作业票可以删除");
        }
        for (EhsPermit child : children(permit.getId())) {
            permitMapper.deleteById(child);
        }
        permitMapper.deleteById(permit);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        AuthUser authUser = AuthContextHolder.requireUser();
        EhsPermit permit = requirePermit(id);
        ensureApplicantOrAdmin(permit, authUser);
        submitPermit(permit, authUser);
    }

    private void submitPermit(EhsPermit permit, AuthUser authUser) {
        if (!Set.of("DRAFT", "RETURNED").contains(permit.getStatus())) {
            throw new BizException("EHS_PERMIT_SUBMIT_INVALID", "当前状态不能提交审批");
        }
        if (permit.getParentId() == null) {
            createMissingChildren(permit);
        }
        startApproval(permit, authUser);
        if (permit.getParentId() == null) {
            for (EhsPermit child : children(permit.getId())) {
                if (Set.of("DRAFT", "RETURNED").contains(child.getStatus())) {
                    startApproval(child, authUser);
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, EhsPermitApprovalDTO dto) {
        approveAs(requirePermit(id), dto, AuthContextHolder.requireUser(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approvePublic(String rawToken, EhsPermitApprovalDTO dto) {
        EhsPublicAccessToken access = publicAccessService.requireApprover(rawToken);
        SysUser user = requireActiveUser(access.getUserId());
        AuthUser authUser = AuthUser.builder()
            .userId(user.getId())
            .username(user.getUsername())
            .realName(user.getRealName())
            .employeeNo(user.getEmployeeNo())
            .userType(user.getUserType())
            .regionId(user.getRegionId())
            .deptCode(user.getDeptCode())
            .deptName(user.getDeptName())
            .roleCodes(userRoleMapper.selectRoleCodesByUserId(user.getId()))
            .build();
        approveAs(requirePermit(access.getPermitId()), dto, authUser, access.getId());
    }

    private void approveAs(EhsPermit permit, EhsPermitApprovalDTO dto, AuthUser authUser, Long publicTokenId) {
        if (!"APPROVING".equals(permit.getApprovalStatus()) || permit.getApprovalInstanceId() == null) {
            throw new BizException("EHS_PERMIT_APPROVAL_INVALID", "当前作业票不在审批中");
        }
        ApprovalInstance instance = approvalInstanceMapper.selectById(permit.getApprovalInstanceId());
        if (instance == null) {
            throw new BizException("APPROVAL_INSTANCE_NOT_FOUND", "统一审批实例不存在");
        }
        String action = normalizeCode(dto.getAction());
        if (!Set.of("APPROVE", "RETURN").contains(action)) {
            throw new BizException("APPROVAL_ACTION_INVALID", "审批动作只支持通过或退回");
        }
        LocalDateTime now = LocalDateTime.now();
        String fromStatus = permit.getApprovalStatus();
        boolean levelCompleted = approvalTaskService.completeBusinessTask(instance, authUser, action, dto.getComment(), now);
        List<ApprovalStep> steps = approvalSteps(permit);
        int currentLevel = permit.getCurrentLevelNo() == null ? steps.get(0).levelNo() : permit.getCurrentLevelNo();
        int currentIndex = stepIndex(steps, currentLevel);
        ApprovalStep currentStep = steps.get(currentIndex);
        if (publicTokenId != null) {
            publicAccessService.markUsed(publicTokenId);
        } else {
            publicAccessService.revokeApproverTokens(permit.getId(), authUser.getUserId());
        }

        if ("RETURN".equals(action)) {
            publicAccessService.revokeApproverTokens(permit.getId());
            permit.setApprovalStatus("RETURNED");
            permit.setStatus("RETURNED");
            permit.setCurrentApproverId(null);
            permit.setCurrentApproverName(null);
            instance.setStatus("RETURNED");
            instance.setCurrentApproverId(null);
            instance.setEndedAt(now);
            recordAction(instance, permit, authUser, action, dto.getComment(), fromStatus, "RETURNED", currentStep, true, true);
            notifyApplicant(permit, "APPROVAL_RETURNED", "EHS作业票已退回",
                permit.getPermitNo() + " 已退回：" + text(dto.getComment(), "请修改后重新提交"));
        } else if (!levelCompleted) {
            recordAction(instance, permit, authUser, action, dto.getComment(), fromStatus, "APPROVING", currentStep, false, false);
            instance.setLastAction("APPROVE");
            instance.setLastActionBy(authUser.getUserId());
            instance.setLastActionAt(now);
            approvalInstanceMapper.updateById(instance);
            permitMapper.updateById(permit);
            return;
        } else if (currentIndex + 1 < steps.size()) {
            ApprovalStep nextStep = steps.get(currentIndex + 1);
            applyCurrentStep(permit, instance, nextStep);
            instance.setLastAction("APPROVE");
            instance.setLastActionBy(authUser.getUserId());
            instance.setLastActionAt(now);
            recordAction(instance, permit, authUser, action, dto.getComment(), fromStatus, "APPROVING", currentStep, false, false);
            approvalInstanceMapper.updateById(instance);
            permitMapper.updateById(permit);
            createTasksAndNotify(permit, instance, nextStep, now);
            return;
        } else {
            publicAccessService.revokeApproverTokens(permit.getId());
            permit.setApprovalStatus("APPROVED");
            permit.setCurrentApproverId(null);
            permit.setCurrentApproverName(null);
            permit.setStatus(permit.getParentId() == null && !children(permit.getId()).isEmpty()
                ? "PENDING_SUB_PERMITS"
                : "APPROVED");
            instance.setStatus("APPROVED");
            instance.setCurrentApproverId(null);
            instance.setEndedAt(now);
            recordAction(instance, permit, authUser, action, dto.getComment(), fromStatus, "APPROVED", currentStep, true, false);
            notifyApplicant(permit, "APPROVAL_APPROVED", "EHS作业票审批通过", permit.getPermitNo() + " 已完成审批");
        }
        instance.setLastAction(action);
        instance.setLastActionBy(authUser.getUserId());
        instance.setLastActionAt(now);
        approvalInstanceMapper.updateById(instance);
        permitMapper.updateById(permit);
        refreshParentApprovalStatus(permit);
    }

    @Transactional(rollbackFor = Exception.class)
    public void start(Long id) {
        AuthUser authUser = AuthContextHolder.requireUser();
        EhsPermit permit = requirePermit(id);
        if (permit.getParentId() != null) {
            throw new BizException("EHS_PERMIT_START_PARENT_ONLY", "请从总作业票发起开工");
        }
        ensureApplicant(permit, authUser);
        startPermit(permit);
    }

    @Transactional(rollbackFor = Exception.class)
    public void startPublic(String rawToken, Long permitId) {
        EhsPublicAccessToken access = publicAccessService.requireApplicant(rawToken);
        EhsPermit permit = requirePublicTarget(access, permitId);
        if (permit.getParentId() != null) {
            throw new BizException("EHS_PERMIT_START_PARENT_ONLY", "请从总作业票发起开工");
        }
        startPermit(permit);
    }

    private void startPermit(EhsPermit permit) {
        if (!"APPROVED".equals(permit.getStatus())) {
            throw new BizException("EHS_PERMIT_START_INVALID", "总票和全部专项票审批通过后才能开工");
        }
        LocalDateTime now = LocalDateTime.now();
        permit.setStatus("IN_PROGRESS");
        permit.setActualStartAt(now);
        permitMapper.updateById(permit);
        for (EhsPermit child : children(permit.getId())) {
            child.setStatus("IN_PROGRESS");
            child.setActualStartAt(now);
            permitMapper.updateById(child);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addSample(Long id, EhsPermitSampleDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        EhsPermit permit = requirePermit(id);
        ensureApplicantOrAdmin(permit, authUser);
        addSample(permit, dto, authUser.getUserId(), displayName(authUser));
    }

    @Transactional(rollbackFor = Exception.class)
    public void addSamplePublic(String rawToken, Long permitId, EhsPermitSampleDTO dto) {
        EhsPublicAccessToken access = publicAccessService.requireApplicant(rawToken);
        EhsPermit permit = requirePublicTarget(access, permitId);
        EhsPermit parent = permit.getParentId() == null ? permit : requirePermit(permit.getParentId());
        addSample(permit, dto, null, parent.getApplicantName());
    }

    private void addSample(EhsPermit permit, EhsPermitSampleDTO dto, Long recordedBy, String recordedByName) {
        if (!"CONFINED_SPACE".equals(permit.getPermitType())) {
            throw new BizException("EHS_SAMPLE_PERMIT_INVALID", "只有受限空间作业票可以记录气体采样");
        }
        if (!Set.of("APPROVED", "IN_PROGRESS").contains(permit.getStatus())) {
            throw new BizException("EHS_SAMPLE_STATUS_INVALID", "当前状态不能记录气体采样");
        }
        List<Map<String, Object>> samples = readMapList(permit.getSamplesJson());
        Map<String, Object> sample = objectMapper.convertValue(dto, MAP_TYPE);
        sample.put("recordedBy", recordedBy);
        sample.put("recordedByName", recordedByName);
        sample.put("recordedAt", LocalDateTime.now().toString());
        samples.add(sample);
        permit.setSamplesJson(writeJson(samples));
        permitMapper.updateById(permit);
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, EhsPermitCloseDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        EhsPermit permit = requirePermit(id);
        ensureApplicant(permit, authUser);
        closePermit(permit, dto, authUser.getUserId(), displayName(authUser));
    }

    @Transactional(rollbackFor = Exception.class)
    public void closePublic(String rawToken, Long permitId, EhsPermitCloseDTO dto) {
        EhsPublicAccessToken access = publicAccessService.requireApplicant(rawToken);
        EhsPermit permit = requirePublicTarget(access, permitId);
        EhsPermit parent = permit.getParentId() == null ? permit : requirePermit(permit.getParentId());
        closePermit(permit, dto, null, parent.getApplicantName());
    }

    private void closePermit(EhsPermit permit, EhsPermitCloseDTO dto, Long actorUserId, String actorName) {
        String outcome = normalizeCode(dto.getOutcome());
        if (!Set.of("NORMAL", "CANCELLED", "ABNORMAL").contains(outcome)) {
            throw new BizException("EHS_CLOSE_OUTCOME_INVALID", "关单结果不合法");
        }
        if (dto.getPhotos() != null && dto.getPhotos().size() > 6) {
            throw new BizException("EHS_CLOSE_PHOTO_LIMIT", "关单照片最多 6 张");
        }
        if (permit.getParentId() == null) {
            if (!Set.of("IN_PROGRESS", "PENDING_CLOSE").contains(permit.getStatus())) {
                throw new BizException("EHS_PARENT_CLOSE_STATUS_INVALID", "当前总票不能关单");
            }
            if (children(permit.getId()).stream().anyMatch(child -> !"CLOSED".equals(child.getStatus()))) {
                throw new BizException("EHS_CHILD_PERMIT_NOT_CLOSED", "全部专项票关闭后才能关闭总票");
            }
        } else if (!"IN_PROGRESS".equals(permit.getStatus())) {
            throw new BizException("EHS_CHILD_CLOSE_STATUS_INVALID", "当前专项票不能关单");
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> closure = new LinkedHashMap<>();
        closure.put("outcome", outcome);
        closure.put("actualEndAt", (dto.getActualEndAt() == null ? now : dto.getActualEndAt()).toString());
        closure.put("signature", dto.getSignature().trim());
        closure.put("note", text(dto.getNote(), ""));
        closure.put("photos", dto.getPhotos() == null ? List.of() : dto.getPhotos());
        closure.put("closedByName", actorName);
        permit.setClosureJson(writeJson(closure));
        permit.setCloseOutcome(outcome);
        permit.setCloseNote(limit(dto.getNote(), 1000));
        permit.setClosedBy(actorUserId);
        permit.setClosedAt(now);
        permit.setActualEndAt(dto.getActualEndAt() == null ? now : dto.getActualEndAt());
        permit.setStatus("CLOSED");
        permitMapper.updateById(permit);

        if (permit.getParentId() != null) {
            EhsPermit parent = requirePermit(permit.getParentId());
            if (children(parent.getId()).stream().allMatch(child -> "CLOSED".equals(child.getStatus()))) {
                parent.setStatus("PENDING_CLOSE");
                permitMapper.updateById(parent);
                notifyApplicant(parent, "PARENT_READY_TO_CLOSE", "EHS总作业票可关单",
                    parent.getPermitNo() + " 的专项票已全部关闭，请完成总票关单");
            }
        } else {
            notifyApplicant(permit, "PERMIT_CLOSED", "EHS作业票已关闭", permit.getPermitNo() + " 已完成关单归档");
        }
    }

    private void apply(EhsPermit permit, EhsPermitSaveDTO dto, boolean parent) {
        ProcurementApprovalRoute route = departmentOptionService.requireEnabledRoute(dto.getApprovalRouteId());
        permit.setApprovalRouteId(route.getId());
        permit.setDeptCode(firstText(route.getDeptCode(), route.getCostCenterCode()));
        permit.setDeptName(firstText(route.getDeptName(), route.getRoomName(), route.getBuildingName()));
        permit.setTitle(limit(dto.getTitle(), 255));
        permit.setContractorName(limit(dto.getContractorName(), 255));
        permit.setRegion(limit(dto.getRegion(), 128));
        permit.setLocation(limit(dto.getLocation(), 255));
        permit.setWorkContent(dto.getWorkContent().trim());
        permit.setPlannedStartAt(dto.getPlannedStartAt());
        permit.setPlannedEndAt(dto.getPlannedEndAt());
        if (parent) {
            permit.setRequiredPermitTypesJson(writeJson(normalizeSpecialTypes(dto.getRequiredPermitTypes())));
        }
        permit.setRiskItemsJson(writeJson(dto.getRiskItems()));
        permit.setSafetyNoticeJson(writeJson(dto.getSafetyNotice() == null ? Map.of() : dto.getSafetyNotice()));
        permit.setOperationStepsJson(writeJson(dto.getOperationSteps()));
        permit.setSignaturesJson(writeJson(dto.getSignatures()));
        permit.setAttachmentsJson(writeJson(dto.getAttachments()));
        permit.setRemark(limit(dto.getRemark(), 1000));
    }

    private void createMissingChildren(EhsPermit parent) {
        Set<String> existingTypes = new LinkedHashSet<>();
        for (EhsPermit child : children(parent.getId())) {
            existingTypes.add(child.getPermitType());
        }
        for (String type : readStringList(parent.getRequiredPermitTypesJson())) {
            if (existingTypes.contains(type)) {
                continue;
            }
            EhsPermit child = new EhsPermit();
            BeanUtils.copyProperties(parent, child, "id", "permitNo", "parentId", "permitType", "status",
                "approvalStatus", "currentLevelNo", "currentApproverId", "currentApproverName",
                "approvalInstanceId", "submitRound", "requiredPermitTypesJson", "specialApproverUserIdsJson",
                "actualStartAt", "actualEndAt", "closureJson", "closedBy", "closedAt", "closeOutcome", "closeNote",
                "createdAt", "updatedAt", "createdBy", "updatedBy", "isDeleted", "version");
            child.setPermitNo(nextPermitNo(type));
            child.setParentId(parent.getId());
            child.setPermitType(type);
            child.setTitle(parent.getTitle() + " - " + permitTypeName(type));
            child.setStatus("DRAFT");
            child.setApprovalStatus("DRAFT");
            child.setSubmitRound(0);
            permitMapper.insert(child);
        }
    }

    private void startApproval(EhsPermit permit, AuthUser submitter) {
        List<ApprovalStep> steps = resolveApprovalSteps(permit);
        ApprovalStep firstStep = steps.get(0);
        List<Long> allApprovers = steps.stream()
            .flatMap(step -> step.candidateUserIds().stream())
            .distinct()
            .toList();
        permit.setApprovalSnapshotJson(writeJson(steps));
        permit.setApproverUserIdsJson(writeJson(allApprovers));
        LocalDateTime now = LocalDateTime.now();
        String fromStatus = permit.getStatus();
        int submitRound = (permit.getSubmitRound() == null ? 0 : permit.getSubmitRound()) + 1;
        ApprovalInstance instance = permit.getApprovalInstanceId() == null
            ? null
            : approvalInstanceMapper.selectById(permit.getApprovalInstanceId());
        if (instance == null) {
            instance = new ApprovalInstance();
            instance.setInstanceNo("EHS-AP-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now)
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT));
            instance.setBizType(bizType(permit));
            instance.setBizId(permit.getId());
            instance.setPurchaseRequestId(null);
            instance.setStartedAt(now);
            instance.setRemark(permit.getPermitNo() + " " + permit.getTitle());
            approvalInstanceMapper.insert(instance);
            permit.setApprovalInstanceId(instance.getId());
        }
        applyCurrentStep(permit, instance, firstStep);
        instance.setTotalNodeNo(steps.get(steps.size() - 1).levelNo());
        instance.setLastAction("SUBMIT");
        instance.setLastActionBy(submitter == null ? null : submitter.getUserId());
        instance.setLastActionAt(now);
        instance.setStatus("APPROVING");
        instance.setEndedAt(null);
        approvalInstanceMapper.updateById(instance);

        permit.setStatus("PENDING_APPROVAL");
        permit.setApprovalStatus("APPROVING");
        permit.setSubmitRound(submitRound);
        permitMapper.updateById(permit);
        createTasksAndNotify(permit, instance, firstStep, now);
        recordAction(instance, permit, submitter, "SUBMIT", "提交作业许可审批", fromStatus, "APPROVING", firstStep, false, false);
    }

    private List<ApprovalStep> resolveApprovalSteps(EhsPermit permit) {
        ProcurementApprovalRoute route = departmentOptionService.requireEnabledRoute(permit.getApprovalRouteId());
        permit.setDeptCode(firstText(route.getDeptCode(), route.getCostCenterCode()));
        permit.setDeptName(firstText(route.getDeptName(), route.getRoomName(), route.getBuildingName()));
        PurchaseRequest probe = new PurchaseRequest();
        probe.setApplicantUserId(permit.getApplicantUserId());
        probe.setDeptCode(permit.getDeptCode());
        probe.setDeptName(permit.getDeptName());
        probe.setTotalAmount(BigDecimal.ZERO);
        probe.setUrgencyLevel(1);
        probe.setProfessionalReviewRequired(0);

        ProcurementApprovalRoute matchedRoute = approvalRouteResolver.findRoute(probe);
        if (matchedRoute == null || !Objects.equals(matchedRoute.getId(), route.getId())) {
            throw new BizException(
                "EHS_DEPARTMENT_ROUTE_AMBIGUOUS",
                "所选部门无法唯一匹配部门管理配置，请检查部门编码和名称"
            );
        }

        int currentNode = approvalRouteResolver.firstDemandNodeNo(probe);
        int totalNode = approvalRouteResolver.totalDemandApprovalNodes(probe, BigDecimal.ZERO);
        List<ApprovalStep> steps = new ArrayList<>();
        while (currentNode > 0 && currentNode <= totalNode) {
            ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
                approvalRouteResolver.resolveAssignmentForNode(probe, currentNode);
            if (assignment.candidateUserIds() != null && !assignment.candidateUserIds().isEmpty()) {
                steps.add(ApprovalStep.from(assignment));
            }
            int nextNode = approvalRouteResolver.nextActiveNodeNo(probe, currentNode, totalNode);
            if (nextNode <= currentNode) {
                break;
            }
            currentNode = nextNode;
        }
        if (steps.isEmpty()) {
            throw new BizException("EHS_DEPARTMENT_APPROVER_NOT_CONFIGURED", "所选部门没有可用审批节点，请先在部门管理中配置审批人");
        }
        return steps;
    }

    private void createTasksAndNotify(EhsPermit permit, ApprovalInstance instance, ApprovalStep step, LocalDateTime now) {
        publicAccessService.revokeApproverTokens(permit.getId());
        approvalTaskService.createBusinessTasks(
            instance,
            step.toAssignment(),
            step.roleCode(),
            permit.getSubmitRound(),
            approvalTaskService.businessApprovalDueAt(now)
        );
        for (Long approverId : step.candidateUserIds()) {
            EhsPublicAccessService.IssuedToken token = publicAccessService.issueApproverToken(permit.getId(), approverId);
            notifyApprover(permit, approverId, publicAccessService.reviewUrl(token.rawToken()));
        }
    }

    private void applyCurrentStep(EhsPermit permit, ApprovalInstance instance, ApprovalStep step) {
        permit.setCurrentLevelNo(step.levelNo());
        permit.setCurrentApproverId(step.candidateUserIds().size() == 1 ? step.candidateUserIds().get(0) : null);
        permit.setCurrentApproverName(step.candidateNames());
        instance.setCurrentNodeNo(step.levelNo());
        instance.setCurrentLevelNo(step.levelNo());
        instance.setCurrentLevelCode(step.levelCode());
        instance.setCurrentPassRule(step.passRule());
        instance.setCurrentApproverId(permit.getCurrentApproverId());
    }

    private void notifyApprover(EhsPermit permit, Long approverId, String reviewUrl) {
        String actionText = StringUtils.hasText(reviewUrl)
            ? "\n免登录审批：" + reviewUrl
            : "\n请登录管理平台处理审批。";
        notificationService.notifyUser(bizType(permit), permit.getId(), approverId,
            "APPROVAL_PENDING", "EHS作业票待审批",
            permit.getPermitNo() + "｜" + permit.getTitle() + "\n作业地点：" + permit.getLocation()
                + actionText);
    }

    private void refreshParentApprovalStatus(EhsPermit permit) {
        EhsPermit parent = permit.getParentId() == null ? permit : requirePermit(permit.getParentId());
        if (!"APPROVED".equals(parent.getApprovalStatus())) {
            return;
        }
        List<EhsPermit> children = children(parent.getId());
        if (children.stream().allMatch(child -> "APPROVED".equals(child.getApprovalStatus()))) {
            parent.setStatus("APPROVED");
            permitMapper.updateById(parent);
            notifyApplicant(parent, "ALL_PERMITS_APPROVED", "EHS作业许可可开工",
                parent.getPermitNo() + " 的总票和专项票已全部通过");
        } else {
            parent.setStatus("PENDING_SUB_PERMITS");
            permitMapper.updateById(parent);
        }
    }

    private void recordAction(ApprovalInstance instance,
                              EhsPermit permit,
                              AuthUser operator,
                              String action,
                              String comment,
                              String fromStatus,
                              String toStatus,
                              ApprovalStep step,
                              boolean finalNode,
                              boolean canResubmit) {
        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setPurchaseRequestId(null);
        record.setNodeNo(step.levelNo());
        record.setNodeName("SUBMIT".equals(action) ? "提交审批" : step.levelName());
        record.setApproverUserId(operator == null ? null : operator.getUserId());
        record.setApproverRoleCode(operator == null ? "EXTERNAL_APPLICANT" : firstRole(operator));
        record.setAction(action);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setApprovalComment(limit(comment, 1000));
        record.setOperatedAt(LocalDateTime.now());
        record.setSubmitRound(permit.getSubmitRound() == null ? 1 : permit.getSubmitRound());
        record.setIsFinalNode(finalNode ? 1 : 0);
        record.setCanResubmit(canResubmit ? 1 : 0);
        record.setTransferToUserId(permit.getCurrentApproverId());
        record.setStatus("ACTIVE");
        record.setRemark(permit.getPermitNo());
        approvalRecordMapper.insert(record);
    }

    private EhsPermitVO toVO(EhsPermit permit, AuthUser authUser, boolean includeDetail) {
        EhsPermitVO vo = new EhsPermitVO();
        BeanUtils.copyProperties(permit, vo);
        vo.setCurrentLevelName(currentStepName(permit));
        vo.setRequiredPermitTypes(readStringList(permit.getRequiredPermitTypesJson()));
        vo.setRiskItems(readMapList(permit.getRiskItemsJson()));
        vo.setSafetyNotice(readMap(permit.getSafetyNoticeJson()));
        vo.setOperationSteps(readMapList(permit.getOperationStepsJson()));
        vo.setSignatures(readMapList(permit.getSignaturesJson()));
        vo.setAttachments(readMapList(permit.getAttachmentsJson()));
        vo.setSamples(readMapList(permit.getSamplesJson()));
        vo.setClosure(readMap(permit.getClosureJson()));
        boolean owner = Objects.equals(permit.getApplicantUserId(), authUser.getUserId());
        vo.setCanEdit((owner || DataScopeHelper.isSystemAdmin(authUser)) && Set.of("DRAFT", "RETURNED").contains(permit.getStatus()));
        vo.setCanSubmit(vo.isCanEdit());
        ApprovalInstance instance = permit.getApprovalInstanceId() == null ? null : approvalInstanceMapper.selectById(permit.getApprovalInstanceId());
        vo.setCanApprove("APPROVING".equals(permit.getApprovalStatus())
            && (DataScopeHelper.isSystemAdmin(authUser) || approvalTaskService.hasPendingBusinessTask(instance, authUser)));
        vo.setCanStart(owner && permit.getParentId() == null && "APPROVED".equals(permit.getStatus()));
        boolean childrenClosed = permit.getParentId() != null || children(permit.getId()).stream().allMatch(child -> "CLOSED".equals(child.getStatus()));
        vo.setCanClose(owner && childrenClosed && ("IN_PROGRESS".equals(permit.getStatus()) || "PENDING_CLOSE".equals(permit.getStatus())));
        if (includeDetail) {
            vo.setChildren(children(permit.getId()).stream().map(child -> toVO(child, authUser, false)).toList());
            vo.setApprovalRecords(approvalRecords(permit));
        }
        return vo;
    }

    public EhsPublicPermitVO publicDetail(String rawToken) {
        EhsPublicAccessToken access = publicAccessService.requireApplicant(rawToken);
        return toPublicVO(requirePermit(access.getPermitId()), true, true, null);
    }

    public EhsPublicPermitVO publicReview(String rawToken) {
        EhsPublicAccessToken access = publicAccessService.requireApprover(rawToken);
        SysUser approver = requireActiveUser(access.getUserId());
        return toPublicVO(requirePermit(access.getPermitId()), true, false, authUser(approver));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePublic(String rawToken, Long permitId, EhsPublicPermitSaveDTO dto) {
        if (StringUtils.hasText(dto.getWebsite())) {
            throw new BizException("EHS_PUBLIC_REQUEST_INVALID", "申请内容校验失败");
        }
        EhsPublicAccessToken access = publicAccessService.requireApplicant(rawToken);
        EhsPermit permit = requirePublicTarget(access, permitId);
        if (!Set.of("DRAFT", "RETURNED").contains(permit.getStatus())) {
            throw new BizException("EHS_PERMIT_NOT_EDITABLE", "当前状态的作业票不能修改");
        }
        EhsPermitSaveDTO save = toInternalSave(dto);
        validateDates(save);
        apply(permit, save, permit.getParentId() == null);
        if (permit.getParentId() == null) {
            permit.setApplicantName(limit(dto.getApplicantName(), 128));
            permit.setApplicantPhone(limit(dto.getApplicantPhone(), 32));
            permit.setApplicantCompany(limit(dto.getApplicantCompany(), 255));
        }
        permitMapper.updateById(permit);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitPublic(String rawToken, Long permitId) {
        EhsPublicAccessToken access = publicAccessService.requireApplicant(rawToken);
        submitPermit(requirePublicTarget(access, permitId), null);
    }

    private EhsPublicPermitVO toPublicVO(EhsPermit permit,
                                        boolean includeDetail,
                                        boolean applicantAccess,
                                        AuthUser approverAuth) {
        EhsPublicPermitVO vo = new EhsPublicPermitVO();
        BeanUtils.copyProperties(permit, vo);
        if (!applicantAccess) {
            vo.setApplicantPhone(null);
        }
        vo.setCurrentLevelName(currentStepName(permit));
        vo.setRequiredPermitTypes(readStringList(permit.getRequiredPermitTypesJson()));
        vo.setRiskItems(readMapList(permit.getRiskItemsJson()));
        vo.setSafetyNotice(readMap(permit.getSafetyNoticeJson()));
        vo.setOperationSteps(readMapList(permit.getOperationStepsJson()));
        vo.setSamples(readMapList(permit.getSamplesJson()));
        vo.setClosure(readMap(permit.getClosureJson()));
        vo.setCanEdit(applicantAccess && Set.of("DRAFT", "RETURNED").contains(permit.getStatus()));
        vo.setCanSubmit(vo.isCanEdit());
        ApprovalInstance instance = permit.getApprovalInstanceId() == null ? null : approvalInstanceMapper.selectById(permit.getApprovalInstanceId());
        vo.setCanApprove(approverAuth != null && "APPROVING".equals(permit.getApprovalStatus())
            && approvalTaskService.hasPendingBusinessTask(instance, approverAuth));
        vo.setCanStart(applicantAccess && permit.getParentId() == null && "APPROVED".equals(permit.getStatus()));
        vo.setCanSample(applicantAccess && "CONFINED_SPACE".equals(permit.getPermitType())
            && Set.of("APPROVED", "IN_PROGRESS").contains(permit.getStatus()));
        boolean childrenClosed = permit.getParentId() != null
            || children(permit.getId()).stream().allMatch(child -> "CLOSED".equals(child.getStatus()));
        vo.setCanClose(applicantAccess && childrenClosed
            && ("IN_PROGRESS".equals(permit.getStatus()) || "PENDING_CLOSE".equals(permit.getStatus())));
        if (includeDetail) {
            vo.setChildren(children(permit.getId()).stream()
                .map(child -> toPublicVO(child, false, applicantAccess, approverAuth))
                .toList());
            vo.setApprovalRecords(publicApprovalRecords(permit));
        }
        return vo;
    }

    private List<EhsPublicApprovalRecordVO> publicApprovalRecords(EhsPermit permit) {
        if (permit.getApprovalInstanceId() == null) {
            return List.of();
        }
        return approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getInstanceId, permit.getApprovalInstanceId())
                .orderByAsc(ApprovalRecord::getOperatedAt)
                .orderByAsc(ApprovalRecord::getId))
            .stream()
            .map(record -> {
                SysUser user = record.getApproverUserId() == null ? null : userMapper.selectById(record.getApproverUserId());
                return EhsPublicApprovalRecordVO.builder()
                    .nodeNo(record.getNodeNo())
                    .nodeName(record.getNodeName())
                    .approverName(user == null ? "外部申请人" : displayName(user))
                    .action(record.getAction())
                    .approvalComment(record.getApprovalComment())
                    .operatedAt(record.getOperatedAt())
                    .build();
            })
            .toList();
    }

    private List<ApprovalRecordVO> approvalRecords(EhsPermit permit) {
        if (permit.getApprovalInstanceId() == null) {
            return List.of();
        }
        return approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getInstanceId, permit.getApprovalInstanceId())
                .orderByAsc(ApprovalRecord::getOperatedAt)
                .orderByAsc(ApprovalRecord::getId))
            .stream()
            .map(this::toApprovalRecordVO)
            .toList();
    }

    private ApprovalRecordVO toApprovalRecordVO(ApprovalRecord record) {
        ApprovalRecordVO vo = new ApprovalRecordVO();
        BeanUtils.copyProperties(record, vo);
        SysUser user = record.getApproverUserId() == null ? null : userMapper.selectById(record.getApproverUserId());
        vo.setApproverName(user == null ? null : displayName(user));
        return vo;
    }

    private List<EhsPermit> children(Long parentId) {
        if (parentId == null) {
            return List.of();
        }
        return permitMapper.selectList(new LambdaQueryWrapper<EhsPermit>()
            .eq(EhsPermit::getParentId, parentId)
            .orderByAsc(EhsPermit::getId));
    }

    private EhsPermit requirePublicTarget(EhsPublicAccessToken access, Long targetPermitId) {
        if (access == null || access.getPermitId() == null || targetPermitId == null) {
            throw new BizException("EHS_PUBLIC_TOKEN_INVALID", "访问链接无效或已过期");
        }
        EhsPermit target = requirePermit(targetPermitId);
        if (!Objects.equals(target.getId(), access.getPermitId())
            && !Objects.equals(target.getParentId(), access.getPermitId())) {
            throw new BizException("EHS_PUBLIC_TOKEN_INVALID", "访问链接无效或已过期");
        }
        return target;
    }

    private EhsPermitSaveDTO toInternalSave(EhsPublicPermitSaveDTO dto) {
        EhsPermitSaveDTO save = new EhsPermitSaveDTO();
        save.setApprovalRouteId(dto.getApprovalRouteId());
        save.setTitle(dto.getTitle());
        save.setContractorName(dto.getContractorName());
        save.setRegion(dto.getRegion());
        save.setLocation(dto.getLocation());
        save.setWorkContent(dto.getWorkContent());
        save.setPlannedStartAt(dto.getPlannedStartAt());
        save.setPlannedEndAt(dto.getPlannedEndAt());
        save.setRequiredPermitTypes(dto.getRequiredPermitTypes());
        save.setRiskItems(dto.getRiskItems());
        save.setSafetyNotice(dto.getSafetyNotice());
        save.setOperationSteps(dto.getOperationSteps());
        save.setSignatures(dto.getSignatures());
        save.setAttachments(dto.getAttachments());
        save.setRemark(dto.getRemark());
        return save;
    }

    private AuthUser authUser(SysUser user) {
        return AuthUser.builder()
            .userId(user.getId())
            .username(user.getUsername())
            .realName(user.getRealName())
            .employeeNo(user.getEmployeeNo())
            .userType(user.getUserType())
            .regionId(user.getRegionId())
            .deptCode(user.getDeptCode())
            .deptName(user.getDeptName())
            .roleCodes(userRoleMapper.selectRoleCodesByUserId(user.getId()))
            .build();
    }

    private List<ApprovalStep> approvalSteps(EhsPermit permit) {
        List<ApprovalStep> steps = read(permit.getApprovalSnapshotJson(), APPROVAL_STEP_LIST, new ArrayList<>());
        if (!steps.isEmpty()) {
            return steps;
        }
        List<Long> legacyApprovers = readLongList(permit.getApproverUserIdsJson());
        for (int index = 0; index < legacyApprovers.size(); index++) {
            Long userId = legacyApprovers.get(index);
            SysUser user = requireActiveUser(userId);
            int levelNo = index + 1;
            steps.add(new ApprovalStep(
                levelNo,
                "EHS_LEVEL_" + levelNo,
                "EHS第" + levelNo + "级审批",
                "EHS_APPROVER",
                ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
                List.of(userId),
                displayName(user)
            ));
        }
        if (steps.isEmpty()) {
            throw new BizException("EHS_APPROVAL_SNAPSHOT_MISSING", "作业票没有可用审批节点，请重新提交");
        }
        return steps;
    }

    private int stepIndex(List<ApprovalStep> steps, int levelNo) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).levelNo() == levelNo) {
                return index;
            }
        }
        throw new BizException("EHS_APPROVAL_STEP_INVALID", "当前审批节点与提交快照不一致");
    }

    private String currentStepName(EhsPermit permit) {
        if (permit.getCurrentLevelNo() == null) {
            return null;
        }
        try {
            List<ApprovalStep> steps = approvalSteps(permit);
            return steps.get(stepIndex(steps, permit.getCurrentLevelNo())).levelName();
        } catch (BizException ignored) {
            return null;
        }
    }

    private void notifyApplicant(EhsPermit permit, String eventType, String title, String content) {
        if (permit.getApplicantUserId() != null) {
            notificationService.notifyUser(bizType(permit), permit.getId(), permit.getApplicantUserId(), eventType, title, content);
        }
    }

    private SysUser requireActiveUser(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException("EHS_APPROVER_INVALID", "所选审批人不存在或已停用");
        }
        return user;
    }

    private EhsPermit requirePermit(Long id) {
        EhsPermit permit = id == null ? null : permitMapper.selectById(id);
        if (permit == null) {
            throw new BizException("EHS_PERMIT_NOT_FOUND", "EHS作业票不存在");
        }
        return permit;
    }

    private void ensureApplicantOrAdmin(EhsPermit permit, AuthUser authUser) {
        if (!Objects.equals(permit.getApplicantUserId(), authUser.getUserId()) && !DataScopeHelper.isSystemAdmin(authUser)) {
            throw new BizException("FORBIDDEN", "只有申请人或系统管理员可以修改该作业票");
        }
    }

    private void ensureApplicant(EhsPermit permit, AuthUser authUser) {
        if (!Objects.equals(permit.getApplicantUserId(), authUser.getUserId())) {
            throw new BizException("FORBIDDEN", "只有申请人可以执行该操作");
        }
    }

    private void validateDates(EhsPermitSaveDTO dto) {
        if (dto.getPlannedStartAt() != null && dto.getPlannedEndAt() != null
            && !dto.getPlannedEndAt().isAfter(dto.getPlannedStartAt())) {
            throw new BizException("EHS_PERMIT_DATE_INVALID", "计划结束时间必须晚于开始时间");
        }
    }

    private List<String> normalizeSpecialTypes(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            String type = normalizePermitType(value);
            if (TYPE_WORK_PERMIT.equals(type)) {
                continue;
            }
            if (!result.contains(type)) {
                result.add(type);
            }
        }
        return result;
    }

    private String normalizePermitType(String value) {
        String type = normalizeCode(value);
        if (!TYPE_WORK_PERMIT.equals(type) && !SPECIAL_TYPES.contains(type)) {
            throw new BizException("EHS_PERMIT_TYPE_INVALID", "不支持的作业票类型");
        }
        return type;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String bizType(EhsPermit permit) {
        return permit.getParentId() == null ? "EHS_WORK_PERMIT" : "EHS_SPECIAL_PERMIT";
    }

    private String nextPermitNo(String permitType) {
        String prefix = TYPE_WORK_PERMIT.equals(permitType) ? "PTW" : "SP";
        return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
            + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private String permitTypeName(String type) {
        return switch (type) {
            case "HOT_WORK" -> "动火作业票";
            case "HEIGHT_WORK" -> "高处作业票";
            case "CONFINED_SPACE" -> "受限空间作业票";
            case "ELECTRIC_WORK" -> "临时用电作业票";
            case "LIFTING_WORK" -> "吊装作业票";
            case "EXCAVATION_WORK" -> "动土作业票";
            default -> "总作业票";
        };
    }

    private String displayName(AuthUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
    }

    private String firstRole(AuthUser user) {
        return user.getRoleCodes() == null || user.getRoleCodes().isEmpty() ? null : user.getRoleCodes().get(0);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new BizException("EHS_JSON_INVALID", "作业票结构化内容格式不正确");
        }
    }

    private List<Long> readLongList(String value) {
        return read(value, LONG_LIST, new ArrayList<>());
    }

    private List<String> readStringList(String value) {
        return read(value, STRING_LIST, new ArrayList<>());
    }

    private List<Map<String, Object>> readMapList(String value) {
        return read(value, MAP_LIST, new ArrayList<>());
    }

    private Map<String, Object> readMap(String value) {
        return read(value, MAP_TYPE, new LinkedHashMap<>());
    }

    private <T> T read(String value, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record ApprovalStep(
        int levelNo,
        String levelCode,
        String levelName,
        String roleCode,
        String passRule,
        List<Long> candidateUserIds,
        String candidateNames
    ) {
        private static ApprovalStep from(ProcurementApprovalRouteResolver.ApprovalAssignment assignment) {
            return new ApprovalStep(
                assignment.levelNo(),
                assignment.levelCode(),
                assignment.levelName(),
                ProcurementWorkflow.roleCodeForNode(assignment.levelNo()),
                assignment.passRule(),
                List.copyOf(assignment.candidateUserIds()),
                assignment.candidateNames()
            );
        }

        private ProcurementApprovalRouteResolver.ApprovalAssignment toAssignment() {
            Long currentApproverId = candidateUserIds.size() == 1 ? candidateUserIds.get(0) : null;
            return new ProcurementApprovalRouteResolver.ApprovalAssignment(
                currentApproverId,
                candidateUserIds,
                candidateNames,
                true,
                null,
                null,
                levelNo,
                levelCode,
                levelName,
                passRule,
                true
            );
        }
    }
}
