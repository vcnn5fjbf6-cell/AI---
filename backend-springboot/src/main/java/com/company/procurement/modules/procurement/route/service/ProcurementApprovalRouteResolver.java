package com.company.procurement.modules.procurement.route.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.org.policy.entity.ProcurementApprovalLevel;
import com.company.procurement.modules.org.policy.entity.ProcurementApprovalLevelUser;
import com.company.procurement.modules.org.policy.entity.ProcurementApprovalPolicy;
import com.company.procurement.modules.org.policy.mapper.ProcurementApprovalLevelMapper;
import com.company.procurement.modules.org.policy.mapper.ProcurementApprovalLevelUserMapper;
import com.company.procurement.modules.org.policy.mapper.ProcurementApprovalPolicyMapper;
import com.company.procurement.modules.org.scope.entity.OrgScope;
import com.company.procurement.modules.org.scope.mapper.OrgScopeMapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.mapper.ProcurementApprovalRouteMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ProcurementApprovalRouteResolver {

    public static final String PASS_RULE_ANY_ONE = "ANY_ONE";
    public static final String PASS_RULE_ALL = "ALL";
    public static final int LEVEL_DUTY_LEADER = 1;
    public static final int LEVEL_PROFESSIONAL_ENGINEER = 2;
    public static final int LEVEL_FLOOR_LEADER = 3;
    public static final int LEVEL_MANAGER = 4;
    public static final int LEVEL_REGION_MANAGER = 5;
    public static final int LEVEL_DIRECTOR = 6;

    private final ProcurementApprovalRouteMapper routeMapper;
    private final SysUserMapper userMapper;
    private final OrgScopeMapper orgScopeMapper;
    private final ProcurementApprovalPolicyMapper policyMapper;
    private final ProcurementApprovalLevelMapper levelMapper;
    private final ProcurementApprovalLevelUserMapper levelUserMapper;

    public ApprovalAssignment resolveAssignment(PurchaseRequest request, String roleCode) {
        return resolveAssignmentForNode(request, ProcurementWorkflow.nodeNoForRole(roleCode), roleCode);
    }

    public ApprovalAssignment resolveAssignmentForNode(PurchaseRequest request, int nodeNo) {
        return resolveAssignmentForNode(request, nodeNo, ProcurementWorkflow.roleCodeForNode(nodeNo));
    }

    public int firstDemandNodeNo(PurchaseRequest request) {
        ProcurementApprovalPolicy policy = findPolicy(request);
        int duty = firstTriggeredNode(policy, request, LEVEL_DUTY_LEADER, false);
        if (duty > 0) {
            return duty;
        }
        int professional = firstTriggeredNode(policy, request, LEVEL_PROFESSIONAL_ENGINEER, true);
        if (professional > 0) {
            return professional;
        }
        return LEVEL_FLOOR_LEADER;
    }

    public int totalDemandApprovalNodes(PurchaseRequest request, BigDecimal activeAmount) {
        ProcurementApprovalPolicy policy = findPolicy(request);
        if (policy != null) {
            if (amountLevelTriggered(policy, LEVEL_DIRECTOR, activeAmount, policy.getDirectorThreshold())) {
                return LEVEL_DIRECTOR;
            }
            if (amountLevelTriggered(policy, LEVEL_REGION_MANAGER, activeAmount, policy.getRegionThreshold())) {
                return LEVEL_REGION_MANAGER;
            }
            return LEVEL_MANAGER;
        }
        BigDecimal directorThreshold = policy == null || policy.getDirectorThreshold() == null
            ? ProcurementWorkflow.DIRECTOR_APPROVAL_THRESHOLD
            : policy.getDirectorThreshold();
        BigDecimal regionThreshold = policy == null || policy.getRegionThreshold() == null
            ? ProcurementWorkflow.REGION_APPROVAL_THRESHOLD
            : policy.getRegionThreshold();
        if (activeAmount != null && activeAmount.compareTo(directorThreshold) > 0) {
            return LEVEL_DIRECTOR;
        }
        if (activeAmount != null && activeAmount.compareTo(regionThreshold) > 0) {
            return LEVEL_REGION_MANAGER;
        }
        return LEVEL_MANAGER;
    }

    public int totalQuoteApprovalNodes(PurchaseRequest request, BigDecimal amount) {
        return totalDemandApprovalNodes(request, amount);
    }

    public int nextActiveNodeNo(PurchaseRequest request, int currentNodeNo, int totalNodeNo) {
        return nextActiveNodeNo(request, currentNodeNo, totalNodeNo, null);
    }

    public int nextActiveNodeNo(PurchaseRequest request, int currentNodeNo, int totalNodeNo, Long approvedUserId) {
        int startNodeNo = currentNodeNo + 1;
        ProcurementApprovalPolicy policy = findPolicy(request);
        if (approvedUserId != null && policy != null && enabled(policy.getSkipLowerWhenHigherApproved())) {
            int highestUserLevelNo = highestConfiguredLevelNo(policy.getId(), approvedUserId);
            if (highestUserLevelNo > currentNodeNo) {
                startNodeNo = highestUserLevelNo + 1;
            }
        }
        if (startNodeNo > totalNodeNo) {
            return 0;
        }
        for (int nodeNo = startNodeNo; nodeNo <= totalNodeNo; nodeNo++) {
            ApprovalAssignment assignment = resolveConfiguredAssignmentOrNull(
                request,
                nodeNo,
                ProcurementWorkflow.roleCodeForNode(nodeNo)
            );
            if (assignment != null && !assignment.candidateUserIds().isEmpty()) {
                return assignment.levelNo();
            }
        }
        if (totalNodeNo >= LEVEL_MANAGER) {
            return resolveAvailableAssignmentFromNode(request, startNodeNo, totalNodeNo).levelNo();
        }
        return 0;
    }

    public ApprovalAssignment resolveAvailableAssignmentFromNode(PurchaseRequest request, int startNodeNo, int configuredTotalNodeNo) {
        int scanStartNodeNo = Math.max(startNodeNo, LEVEL_MANAGER);
        int scanEndNodeNo = ProcurementWorkflow.totalApprovalNodes();
        for (int nodeNo = scanStartNodeNo; nodeNo <= scanEndNodeNo; nodeNo++) {
            ApprovalAssignment assignment = resolveConfiguredAssignmentOrNull(
                request,
                nodeNo,
                ProcurementWorkflow.roleCodeForNode(nodeNo)
            );
            if (assignment != null && !assignment.candidateUserIds().isEmpty()) {
                return assignment;
            }
        }
        throw new BizException(
            "APPROVER_NOT_CONFIGURED",
            "No configured V1-V8 approver from node V" + Math.max(scanStartNodeNo, configuredTotalNodeNo)
        );
    }

    public ProcurementApprovalPolicy findPolicy(PurchaseRequest request) {
        if (request == null) {
            return null;
        }
        OrgScope scope = findScope(request);
        if (scope == null) {
            return null;
        }
        ProcurementApprovalPolicy policy = policyMapper.selectOne(new LambdaQueryWrapper<ProcurementApprovalPolicy>()
            .eq(ProcurementApprovalPolicy::getScopeId, scope.getId())
            .eq(ProcurementApprovalPolicy::getEnabled, 1)
            .last("LIMIT 1"));
        if (policy != null) {
            return policy;
        }
        return null;
    }

    public OrgScope findScope(PurchaseRequest request) {
        if (request == null) {
            return null;
        }
        List<OrgScope> scopes = orgScopeMapper.selectList(new LambdaQueryWrapper<OrgScope>()
            .eq(OrgScope::getStatus, 1)
            .eq(OrgScope::getScopeType, "BUILDING")
            .orderByAsc(OrgScope::getRegionName)
            .orderByAsc(OrgScope::getRoomName)
            .orderByAsc(OrgScope::getBuildingName));
        OrgScope scope = matchScope(scopes, new RouteLookup(request.getDeptCode(), request.getDeptName()));
        if (scope != null) {
            return scope;
        }
        if (request.getApplicantUserId() != null) {
            SysUser applicant = userMapper.selectById(request.getApplicantUserId());
            if (applicant != null) {
                OrgScope applicantScope = matchScope(scopes, new RouteLookup(applicant.getDeptCode(), applicant.getDeptName()));
                if (applicantScope != null) {
                    return applicantScope;
                }
            }
        }
        return null;
    }

    public void validateRouteCandidates(ProcurementApprovalRoute route) {
        validateRouteCandidates(route, ProcurementWorkflow.ROLE_FLOOR_LEADER);
        validateRouteCandidates(route, ProcurementWorkflow.ROLE_MANAGER);
        validateRouteCandidates(route, ProcurementWorkflow.ROLE_REGION_MANAGER);
        validateRouteCandidates(route, ProcurementWorkflow.ROLE_SENIOR_MANAGER);
    }

    public boolean canApprove(PurchaseRequest request, AuthUser authUser, Integer nodeNo, Long currentApproverId) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        int currentNodeNo = nodeNo == null || nodeNo < 1 ? firstDemandNodeNo(request) : nodeNo;
        ApprovalAssignment assignment = resolveAssignmentForNode(request, currentNodeNo);
        if (!assignment.candidateUserIds().isEmpty()) {
            return assignment.candidateUserIds().contains(authUser.getUserId());
        }
        return false;
    }

    public boolean canAccessRelatedApproval(PurchaseRequest request, AuthUser authUser) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        if (request != null && Objects.equals(authUser.getUserId(), request.getApplicantUserId())) {
            return true;
        }
        ProcurementApprovalPolicy policy = findPolicy(request);
        if (policy != null) {
            List<ProcurementApprovalLevel> levels = levels(policy.getId());
            for (ProcurementApprovalLevel level : levels) {
                if (candidateUserIds(level).contains(authUser.getUserId())) {
                    return true;
                }
            }
        }
        ProcurementApprovalRoute route = findRoute(request);
        if (route != null) {
            List<String> names = routeParticipantNames(route);
            List<Long> userIds = resolveUserIds(names);
            if (!userIds.isEmpty() && userIds.contains(authUser.getUserId())) {
                return true;
            }
            return names.stream().anyMatch(name -> authUserMatchesName(authUser, name));
        }
        return false;
    }

    public ProcurementApprovalRoute findRoute(PurchaseRequest request) {
        if (request == null) {
            return null;
        }
        List<ProcurementApprovalRoute> routes = routeMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalRoute>()
            .eq(ProcurementApprovalRoute::getEnabled, 1)
            .orderByAsc(ProcurementApprovalRoute::getRegionName)
            .orderByAsc(ProcurementApprovalRoute::getRoomName)
            .orderByAsc(ProcurementApprovalRoute::getBuildingName));
        if (routes.isEmpty()) {
            return null;
        }

        RouteLookup requestLookup = new RouteLookup(request.getDeptCode(), request.getDeptName());
        ProcurementApprovalRoute route = matchRoute(routes, requestLookup);
        if (route != null) {
            return route;
        }
        if (request.getApplicantUserId() != null) {
            SysUser applicant = userMapper.selectById(request.getApplicantUserId());
            if (applicant != null) {
                ProcurementApprovalRoute applicantRoute = matchRoute(routes, new RouteLookup(applicant.getDeptCode(), applicant.getDeptName()));
                if (applicantRoute != null) {
                    return applicantRoute;
                }
            }
        }
        return null;
    }

    public List<String> candidateNames(ProcurementApprovalRoute route, String roleCode) {
        if (route == null || !StringUtils.hasText(roleCode)) {
            return List.of();
        }
        return switch (roleCode.trim().toUpperCase(Locale.ROOT)) {
            case ProcurementWorkflow.ROLE_FLOOR_LEADER -> splitNames(route.getFloorLeaderNames());
            case ProcurementWorkflow.ROLE_MANAGER -> splitNames(route.getManagerNames());
            case ProcurementWorkflow.ROLE_REGION_MANAGER -> splitNames(route.getRegionManagerNames());
            case ProcurementWorkflow.ROLE_SENIOR_MANAGER -> splitNames(route.getDirectorNames());
            case ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT -> splitNames(route.getAssistantNames());
            default -> List.of();
        };
    }

    public List<Long> routeCandidateUserIds(PurchaseRequest request, String roleCode) {
        ProcurementApprovalRoute route = findRoute(request);
        if (route == null) {
            return List.of();
        }
        if (ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT.equals(roleCode)) {
            return parseUserIds(route.getAssistantUserIds());
        }
        List<String> names = candidateNames(route, roleCode);
        if (names.isEmpty()) {
            return List.of();
        }
        return resolveUserIds(names);
    }

    private ApprovalAssignment resolveAssignmentForNode(PurchaseRequest request, int nodeNo, String roleCode) {
        ProcurementApprovalPolicy policy = findPolicy(request);
        if (policy != null && nodeNo > 0) {
            ProcurementApprovalLevel level = activeLevel(policy.getId(), nodeNo);
            if (level == null || !levelTriggered(level, request)) {
                return skippedAssignment(policy, nodeNo);
            }
            List<Long> userIds = candidateUserIds(level);
            if (!userIds.isEmpty()) {
                Long currentApproverId = userIds.size() == 1 ? userIds.get(0) : null;
                return newApprovalAssignment(policy, level, userIds, currentApproverId, true);
            }
            if (skipIfEmpty(level)) {
                return newApprovalAssignment(policy, level, userIds, null, false);
            }
            throw new BizException("APPROVER_NOT_CONFIGURED", level.getLevelName() + " approver is not configured");
        }

        ProcurementApprovalRoute route = findRoute(request);
        if (route != null) {
            List<String> names = candidateNames(route, roleCode);
            if (!names.isEmpty()) {
                List<Long> userIds = resolveUserIds(names);
                if (userIds.isEmpty()) {
                    throw new BizException(
                        "ROUTE_APPROVER_NOT_CONFIGURED",
                        "Approval route candidates are not bound to active system users for node V" + nodeNo
                    );
                }
                Long currentApproverId = userIds.size() == 1 ? userIds.get(0) : null;
                return new ApprovalAssignment(
                    currentApproverId,
                    userIds,
                    String.join(",", names),
                    true,
                    null,
                    null,
                    nodeNo,
                    "V" + nodeNo,
                    ProcurementWorkflow.nodeNameForNode(nodeNo),
                    PASS_RULE_ANY_ONE,
                    true
                );
            }
        }

        throw new BizException(
            "APPROVER_NOT_CONFIGURED",
            ProcurementWorkflow.nodeNameForNode(nodeNo) + " approver is not configured"
        );
    }

    private ApprovalAssignment resolveConfiguredAssignmentOrNull(PurchaseRequest request, int nodeNo, String roleCode) {
        ProcurementApprovalPolicy policy = findPolicy(request);
        if (policy != null && nodeNo > 0) {
            ProcurementApprovalLevel level = activeLevel(policy.getId(), nodeNo);
            if (level == null || !levelTriggered(level, request)) {
                return null;
            }
            List<Long> userIds = candidateUserIds(level);
            if (userIds.isEmpty()) {
                return null;
            }
            Long currentApproverId = userIds.size() == 1 ? userIds.get(0) : null;
            return newApprovalAssignment(policy, level, userIds, currentApproverId, true);
        }

        ProcurementApprovalRoute route = findRoute(request);
        if (route == null) {
            return null;
        }
        List<String> names = candidateNames(route, roleCode);
        if (names.isEmpty()) {
            return null;
        }
        List<Long> userIds = resolveUserIds(names);
        if (userIds.isEmpty()) {
            return null;
        }
        Long currentApproverId = userIds.size() == 1 ? userIds.get(0) : null;
        return new ApprovalAssignment(
            currentApproverId,
            userIds,
            String.join(",", names),
            true,
            null,
            null,
            nodeNo,
            "V" + nodeNo,
            ProcurementWorkflow.nodeNameForNode(nodeNo),
            PASS_RULE_ANY_ONE,
            true
        );
    }

    private ApprovalAssignment newApprovalAssignment(ProcurementApprovalPolicy policy,
                                                     ProcurementApprovalLevel level,
                                                     List<Long> userIds,
                                                     Long currentApproverId,
                                                     boolean hasConfiguredLevel) {
        String levelCode = StringUtils.hasText(level.getLevelCode())
            ? level.getLevelCode()
            : "V" + level.getLevelNo();
        String levelName = StringUtils.hasText(level.getLevelName())
            ? level.getLevelName()
            : ProcurementWorkflow.nodeNameForNode(level.getLevelNo());
        return new ApprovalAssignment(
            currentApproverId,
            userIds,
            candidateNamesByIds(userIds),
            true,
            policy.getId(),
            level.getId(),
            level.getLevelNo(),
            levelCode,
            levelName,
            normalizedPassRule(level.getPassRule()),
            hasConfiguredLevel
        );
    }

    private ApprovalAssignment skippedAssignment(ProcurementApprovalPolicy policy, int nodeNo) {
        return new ApprovalAssignment(
            null,
            List.of(),
            "",
            true,
            policy.getId(),
            null,
            nodeNo,
            "V" + nodeNo,
            ProcurementWorkflow.nodeNameForNode(nodeNo),
            PASS_RULE_ANY_ONE,
            false
        );
    }

    private int firstTriggeredNode(ProcurementApprovalPolicy policy, PurchaseRequest request, int levelNo, boolean requireProfessional) {
        if (policy == null) {
            return 0;
        }
        ProcurementApprovalLevel level = activeLevel(policy.getId(), levelNo);
        if (level == null || !enabled(level.getEnabled())) {
            return 0;
        }
        if (requireProfessional && !Boolean.TRUE.equals(request != null && request.getProfessionalReviewRequired() != null && request.getProfessionalReviewRequired() == 1)) {
            return 0;
        }
        if (!levelTriggered(level, request)) {
            return 0;
        }
        List<Long> users = candidateUserIds(level);
        if (users.isEmpty() && !skipIfEmpty(level)) {
            throw new BizException("APPROVER_NOT_CONFIGURED", level.getLevelName() + " approver is not configured");
        }
        return users.isEmpty() ? 0 : levelNo;
    }

    private boolean levelTriggered(ProcurementApprovalLevel level, PurchaseRequest request) {
        String mode = level.getTriggerMode() == null ? "ALWAYS" : level.getTriggerMode().trim().toUpperCase(Locale.ROOT);
        if ("MANUAL".equals(mode)) {
            return enabled(level.getEnabled());
        }
        if ("BY_SPECIALTY".equals(mode)) {
            return request != null && request.getProfessionalReviewRequired() != null && request.getProfessionalReviewRequired() == 1;
        }
        return true;
    }

    private boolean amountLevelTriggered(ProcurementApprovalPolicy policy,
                                         int levelNo,
                                         BigDecimal amount,
                                         BigDecimal fallbackThreshold) {
        ProcurementApprovalLevel level = activeLevel(policy.getId(), levelNo);
        if (level == null || !enabled(level.getEnabled())) {
            return false;
        }
        String mode = level.getTriggerMode() == null ? "AMOUNT_GT" : level.getTriggerMode().trim().toUpperCase(Locale.ROOT);
        if (!"AMOUNT_GT".equals(mode)) {
            return levelTriggered(level, null);
        }
        if (amount == null) {
            return false;
        }
        BigDecimal threshold = level.getAmountMin() == null ? fallbackThreshold : level.getAmountMin();
        return threshold != null && amount.compareTo(threshold) > 0;
    }

    private ProcurementApprovalLevel activeLevel(Long policyId, int levelNo) {
        if (policyId == null || levelNo <= 0) {
            return null;
        }
        return levelMapper.selectOne(new LambdaQueryWrapper<ProcurementApprovalLevel>()
            .eq(ProcurementApprovalLevel::getPolicyId, policyId)
            .eq(ProcurementApprovalLevel::getLevelNo, levelNo)
            .eq(ProcurementApprovalLevel::getEnabled, 1)
            .last("LIMIT 1"));
    }

    private List<ProcurementApprovalLevel> levels(Long policyId) {
        if (policyId == null) {
            return List.of();
        }
        return levelMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalLevel>()
            .eq(ProcurementApprovalLevel::getPolicyId, policyId)
            .orderByAsc(ProcurementApprovalLevel::getLevelNo));
    }

    private boolean skipIfEmpty(ProcurementApprovalLevel level) {
        return level != null && level.getSkipIfEmpty() != null && level.getSkipIfEmpty() == 1;
    }

    private List<Long> candidateUserIds(ProcurementApprovalLevel level) {
        if (level == null || level.getId() == null) {
            return List.of();
        }
        return levelUserMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalLevelUser>()
                .eq(ProcurementApprovalLevelUser::getLevelId, level.getId())
                .eq(ProcurementApprovalLevelUser::getStatus, 1)
                .orderByAsc(ProcurementApprovalLevelUser::getSortNo)
                .orderByAsc(ProcurementApprovalLevelUser::getId))
            .stream()
            .map(ProcurementApprovalLevelUser::getUserId)
            .distinct()
            .toList();
    }

    private int highestConfiguredLevelNo(Long policyId, Long userId) {
        if (policyId == null || userId == null) {
            return 0;
        }
        List<ProcurementApprovalLevel> configuredLevels = levels(policyId);
        int highest = 0;
        for (ProcurementApprovalLevel level : configuredLevels) {
            if (level.getLevelNo() == null || !enabled(level.getEnabled())) {
                continue;
            }
            if (candidateUserIds(level).contains(userId)) {
                highest = Math.max(highest, level.getLevelNo());
            }
        }
        return highest;
    }

    private String candidateNamesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<SysUser> users = userMapper.selectBatchIds(ids);
        List<String> names = new ArrayList<>();
        for (Long id : ids) {
            for (SysUser user : users) {
                if (Objects.equals(user.getId(), id)) {
                    names.add(userDisplayName(user));
                    break;
                }
            }
        }
        return String.join(",", names);
    }

    private String userDisplayName(SysUser user) {
        String name = StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
        return name;
    }

    private void validateRouteCandidates(ProcurementApprovalRoute route, String roleCode) {
        List<String> names = candidateNames(route, roleCode);
        if (names.isEmpty()) {
            throw new BizException(
                "ROUTE_APPROVER_NOT_CONFIGURED",
                "Approval route candidates are not configured for role " + roleCode
            );
        }
        if (resolveUserIds(names).isEmpty()) {
            throw new BizException(
                "ROUTE_APPROVER_NOT_CONFIGURED",
                "Approval route candidates are not bound to active system users for role " + roleCode
            );
        }
    }

    private ProcurementApprovalRoute matchRoute(List<ProcurementApprovalRoute> routes, RouteLookup lookup) {
        if (!StringUtils.hasText(lookup.deptCode()) && !StringUtils.hasText(lookup.deptName())) {
            return null;
        }
        String deptCode = normalized(lookup.deptCode());
        String deptName = normalized(lookup.deptName());

        if (StringUtils.hasText(deptCode)) {
            for (ProcurementApprovalRoute route : routes) {
                if (deptCode.equals(normalized(route.getDeptCode())) || deptCode.equals(normalized(route.getCostCenterCode()))) {
                    return route;
                }
            }
        }
        if (StringUtils.hasText(deptName)) {
            for (ProcurementApprovalRoute route : routes) {
                if (deptName.equals(normalized(route.getDeptName()))) {
                    return route;
                }
            }
            ProcurementApprovalRoute route = bestContainingRoute(routes, deptName, ProcurementApprovalRoute::getCostCenterCode);
            if (route != null) {
                return route;
            }
            route = bestContainingRoute(routes, deptName, ProcurementApprovalRoute::getBuildingName);
            if (route != null) {
                return route;
            }
            route = bestContainingRoute(routes, deptName, ProcurementApprovalRoute::getRoomName);
            if (route != null) {
                return route;
            }
        }
        return null;
    }

    private OrgScope matchScope(List<OrgScope> scopes, RouteLookup lookup) {
        if (!StringUtils.hasText(lookup.deptCode()) && !StringUtils.hasText(lookup.deptName())) {
            return null;
        }
        String deptCode = normalized(lookup.deptCode());
        String deptName = normalized(lookup.deptName());

        if (StringUtils.hasText(deptCode)) {
            for (OrgScope scope : scopes) {
                if (deptCode.equals(normalized(scope.getDeptCode())) || deptCode.equals(normalized(scope.getCostCenterCode()))) {
                    return scope;
                }
            }
        }
        if (StringUtils.hasText(deptName)) {
            for (OrgScope scope : scopes) {
                if (deptName.equals(normalized(scope.getDeptName()))) {
                    return scope;
                }
            }
            OrgScope scope = bestContainingScope(scopes, deptName, OrgScope::getCostCenterCode);
            if (scope != null) {
                return scope;
            }
            scope = bestContainingScope(scopes, deptName, OrgScope::getBuildingName);
            if (scope != null) {
                return scope;
            }
            scope = bestContainingScope(scopes, deptName, OrgScope::getRoomName);
            if (scope != null) {
                return scope;
            }
        }
        return null;
    }

    private boolean containsText(String source, String target) {
        return StringUtils.hasText(source) && StringUtils.hasText(target) && source.contains(normalized(target));
    }

    private ProcurementApprovalRoute bestContainingRoute(
        List<ProcurementApprovalRoute> routes,
        String source,
        Function<ProcurementApprovalRoute, String> targetGetter
    ) {
        return uniqueLongestContaining(routes, source, targetGetter);
    }

    private OrgScope bestContainingScope(
        List<OrgScope> scopes,
        String source,
        Function<OrgScope, String> targetGetter
    ) {
        return uniqueLongestContaining(scopes, source, targetGetter);
    }

    private <T> T uniqueLongestContaining(List<T> rows, String source, Function<T, String> targetGetter) {
        List<T> matches = rows.stream()
            .filter(row -> containsText(source, targetGetter.apply(row)))
            .toList();
        if (matches.isEmpty()) {
            return null;
        }
        int bestLength = matches.stream()
            .map(targetGetter)
            .map(this::normalized)
            .mapToInt(String::length)
            .max()
            .orElse(0);
        List<T> bestMatches = matches.stream()
            .filter(row -> normalized(targetGetter.apply(row)).length() == bestLength)
            .sorted(Comparator.comparing(row -> normalized(targetGetter.apply(row))))
            .toList();
        return bestMatches.size() == 1 ? bestMatches.get(0) : null;
    }

    private List<Long> resolveUserIds(List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getStatus, 1)
            .and(w -> w
                .in(SysUser::getRealName, names)
                .or()
                .in(SysUser::getUsername, names)
                .or()
                .in(SysUser::getMobile, names)));
        Set<Long> ids = new LinkedHashSet<>();
        for (String name : names) {
            for (SysUser user : users) {
                if (userMatchesName(user, name)) {
                    ids.add(user.getId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private boolean authUserMatchesName(AuthUser authUser, String name) {
        if (authUser == null || !StringUtils.hasText(name)) {
            return false;
        }
        String normalizedName = normalized(name);
        return normalizedName.equals(normalized(authUser.getRealName()))
            || normalizedName.equals(normalized(authUser.getUsername()));
    }

    private boolean userMatchesName(SysUser user, String name) {
        if (user == null || !StringUtils.hasText(name)) {
            return false;
        }
        String normalizedName = normalized(name);
        return normalizedName.equals(normalized(user.getRealName()))
            || normalizedName.equals(normalized(user.getUsername()))
            || normalizedName.equals(normalized(user.getMobile()));
    }

    private List<String> routeParticipantNames(ProcurementApprovalRoute route) {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(splitNames(route.getFloorLeaderNames()));
        names.addAll(splitNames(route.getManagerNames()));
        names.addAll(splitNames(route.getRegionManagerNames()));
        names.addAll(splitNames(route.getDirectorNames()));
        names.addAll(splitNames(route.getReplacementConfirmNames()));
        return new ArrayList<>(names);
    }

    private List<String> splitNames(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String item : value.split("[,，、/；;\\s]+")) {
            if (StringUtils.hasText(item)) {
                names.add(item.trim());
            }
        }
        return new ArrayList<>(names);
    }

    private List<Long> parseUserIds(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String item : value.split("[,，、/；;\\s]+")) {
            try {
                ids.add(Long.parseLong(item.trim()));
            } catch (NumberFormatException ignored) {
                // Assistant permissions are stored by user id; ignore legacy names.
            }
        }
        return new ArrayList<>(ids);
    }

    private boolean enabled(Integer value) {
        return value != null && value == 1;
    }

    private String normalizedPassRule(String passRule) {
        if (PASS_RULE_ALL.equalsIgnoreCase(String.valueOf(passRule))) {
            return PASS_RULE_ALL;
        }
        return PASS_RULE_ANY_ONE;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    public record ApprovalAssignment(
        Long currentApproverId,
        List<Long> candidateUserIds,
        String candidateNames,
        boolean routeMatched,
        Long policyId,
        Long levelId,
        Integer levelNo,
        String levelCode,
        String levelName,
        String passRule,
        boolean hasConfiguredLevel
    ) {
    }

    private record RouteLookup(String deptCode, String deptName) {
    }
}
