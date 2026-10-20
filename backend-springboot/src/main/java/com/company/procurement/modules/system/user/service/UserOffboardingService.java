package com.company.procurement.modules.system.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.org.policy.entity.ProcurementApprovalLevelUser;
import com.company.procurement.modules.org.policy.mapper.ProcurementApprovalLevelUserMapper;
import com.company.procurement.modules.system.user.dto.UserOffboardingDTO;
import com.company.procurement.modules.system.user.dto.UserOffboardingReplacementDTO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.UserOffboardingMapper;
import com.company.procurement.modules.system.user.vo.UserOffboardingImpactItemVO;
import com.company.procurement.modules.system.user.vo.UserOffboardingImpactVO;
import com.company.procurement.modules.system.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserOffboardingService {

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_DEPARTED = 2;
    public static final String IMPACT_APPROVAL_CONFIG = "APPROVAL_CONFIG";
    public static final String IMPACT_PENDING_TASK = "PENDING_TASK";

    private final SysUserService userService;
    private final SysUserMapper userMapper;
    private final UserOffboardingMapper offboardingMapper;
    private final ProcurementApprovalLevelUserMapper levelUserMapper;
    private final ApprovalTaskService approvalTaskService;

    public UserOffboardingImpactVO impact(Long userId) {
        UserVO user = checkedEmployee(userId);
        List<UserOffboardingImpactItemVO> configImpacts = offboardingMapper.selectApprovalConfigImpacts(userId);
        List<UserOffboardingImpactItemVO> taskImpacts = offboardingMapper.selectPendingTaskImpacts(userId);
        List<UserOffboardingImpactItemVO> impacts = new ArrayList<>(configImpacts.size() + taskImpacts.size());
        impacts.addAll(configImpacts);
        impacts.addAll(taskImpacts);

        UserOffboardingImpactVO result = new UserOffboardingImpactVO();
        result.setUserId(user.getId());
        result.setUserName(displayName(user));
        result.setStatus(user.getStatus());
        result.setApprovalConfigCount(configImpacts.size());
        result.setPendingTaskCount(taskImpacts.size());
        result.setImpacts(impacts);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void offboard(Long userId, UserOffboardingDTO dto) {
        UserVO target = checkedEmployee(userId);
        AuthUser operator = AuthContextHolder.requireUser();
        if (Objects.equals(operator.getUserId(), userId)) {
            throw new BizException("USER_OFFBOARDING_SELF_FORBIDDEN", "不能为当前登录账号办理离职");
        }
        if (Objects.equals(target.getStatus(), STATUS_DEPARTED)) {
            throw new BizException("USER_ALREADY_DEPARTED", "该人员已经是离职状态");
        }

        List<UserOffboardingImpactItemVO> impacts = currentImpacts(userId);
        Map<String, Long> replacements = replacementMap(dto);
        Set<String> requiredKeys = impacts.stream()
            .map(UserOffboardingImpactItemVO::getImpactKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!requiredKeys.equals(replacements.keySet())) {
            throw new BizException("USER_OFFBOARDING_REPLACEMENT_REQUIRED", "审批影响已变化，请为当前每一项重新选择替换人员");
        }

        Map<Long, SysUser> replacementUsers = validateReplacementUsers(userId, replacements.values());
        List<ApprovalTaskService.PendingTaskReassignment> taskReassignments = new ArrayList<>();
        for (UserOffboardingImpactItemVO item : impacts) {
            Long replacementUserId = replacements.get(item.getImpactKey());
            SysUser replacement = replacementUsers.get(replacementUserId);
            if (IMPACT_APPROVAL_CONFIG.equals(item.getImpactType())) {
                replaceApprovalBinding(item, userId, target, replacement);
            } else if (IMPACT_PENDING_TASK.equals(item.getImpactType())) {
                taskReassignments.add(new ApprovalTaskService.PendingTaskReassignment(
                    item.getReferenceId(), userId, replacementUserId));
            }
        }
        approvalTaskService.reassignPendingTasks(taskReassignments);

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "用户不存在");
        }
        user.setStatus(STATUS_DEPARTED);
        user.setSessionSecret(UUID.randomUUID().toString());
        userMapper.updateById(user);
    }

    private UserVO checkedEmployee(Long userId) {
        UserVO user = userService.detail(userId);
        if (DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(user.getUserType())) {
            throw new BizException("USER_OFFBOARDING_EMPLOYEE_ONLY", "供应商账号请使用停用，不办理员工离职");
        }
        return user;
    }

    private List<UserOffboardingImpactItemVO> currentImpacts(Long userId) {
        List<UserOffboardingImpactItemVO> impacts = new ArrayList<>();
        impacts.addAll(offboardingMapper.selectApprovalConfigImpacts(userId));
        impacts.addAll(offboardingMapper.selectPendingTaskImpacts(userId));
        return impacts;
    }

    private Map<String, Long> replacementMap(UserOffboardingDTO dto) {
        List<UserOffboardingReplacementDTO> rows = dto == null || dto.getReplacements() == null
            ? List.of()
            : dto.getReplacements();
        Map<String, Long> replacements = new LinkedHashMap<>();
        for (UserOffboardingReplacementDTO row : rows) {
            String impactKey = row == null || !StringUtils.hasText(row.getImpactKey())
                ? null
                : row.getImpactKey().trim();
            if (impactKey == null || row.getReplacementUserId() == null) {
                throw new BizException("USER_OFFBOARDING_REPLACEMENT_REQUIRED", "每个审批影响项都必须选择替换人员");
            }
            if (replacements.putIfAbsent(impactKey, row.getReplacementUserId()) != null) {
                throw new BizException("USER_OFFBOARDING_REPLACEMENT_DUPLICATED", "审批影响项不能重复提交");
            }
        }
        return replacements;
    }

    private Map<Long, SysUser> validateReplacementUsers(Long departingUserId, java.util.Collection<Long> replacementIds) {
        Set<Long> ids = replacementIds.stream().collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (ids.contains(departingUserId)) {
            throw new BizException("USER_OFFBOARDING_REPLACEMENT_INVALID", "离职人员不能替换为本人");
        }
        Map<Long, SysUser> users = userMapper.selectBatchIds(ids).stream()
            .collect(Collectors.toMap(SysUser::getId, user -> user));
        for (Long replacementId : ids) {
            userService.detail(replacementId);
            SysUser replacement = users.get(replacementId);
            if (replacement == null || !Objects.equals(replacement.getStatus(), STATUS_ACTIVE)) {
                throw new BizException("USER_OFFBOARDING_REPLACEMENT_INACTIVE", "替换人员必须是启用中的员工账号");
            }
            if (DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(replacement.getUserType())) {
                throw new BizException("USER_OFFBOARDING_REPLACEMENT_INVALID", "供应商账号不能作为内部审批替换人员");
            }
        }
        return users;
    }

    private void replaceApprovalBinding(UserOffboardingImpactItemVO item,
                                        Long departingUserId,
                                        UserVO departingUser,
                                        SysUser replacement) {
        ProcurementApprovalLevelUser binding = levelUserMapper.selectById(item.getReferenceId());
        if (binding == null
            || !Objects.equals(binding.getUserId(), departingUserId)
            || !Objects.equals(binding.getStatus(), 1)) {
            throw new BizException("USER_OFFBOARDING_IMPACT_STALE", "审批配置已变化，请刷新后重新办理离职");
        }
        ProcurementApprovalLevelUser replacementBinding = levelUserMapper.selectOne(
            new LambdaQueryWrapper<ProcurementApprovalLevelUser>()
                .eq(ProcurementApprovalLevelUser::getLevelId, binding.getLevelId())
                .eq(ProcurementApprovalLevelUser::getUserId, replacement.getId())
                .last("LIMIT 1"));
        if (replacementBinding == null) {
            replacementBinding = new ProcurementApprovalLevelUser();
            replacementBinding.setLevelId(binding.getLevelId());
            replacementBinding.setUserId(replacement.getId());
            replacementBinding.setSortNo(binding.getSortNo());
            replacementBinding.setEffectiveFrom(LocalDateTime.now());
            replacementBinding.setStatus(1);
            replacementBinding.setRemark(reassignmentRemark(departingUser, replacement));
            levelUserMapper.insert(replacementBinding);
        } else {
            replacementBinding.setStatus(1);
            replacementBinding.setEffectiveTo(null);
            if (replacementBinding.getEffectiveFrom() == null) {
                replacementBinding.setEffectiveFrom(LocalDateTime.now());
            }
            levelUserMapper.updateById(replacementBinding);
        }

        binding.setStatus(0);
        binding.setEffectiveTo(LocalDateTime.now());
        binding.setRemark(reassignmentRemark(departingUser, replacement));
        levelUserMapper.updateById(binding);
    }

    private String reassignmentRemark(UserVO departingUser, SysUser replacement) {
        return "离职交接：" + displayName(departingUser) + " → " + displayName(replacement);
    }

    private String displayName(UserVO user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
    }
}
