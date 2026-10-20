package com.company.procurement.modules.procurement.route.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.mapper.ProcurementApprovalRouteMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ProcurementDepartmentAccessService {

    private static final String DEFAULT_ACTING_REASON = "管理员代用户选择采购部门";

    private final ProcurementApprovalRouteMapper routeMapper;
    private final ProcurementApprovalRouteService routeService;
    private final SysUserMapper userMapper;

    public List<ProcurementApprovalRoute> listAllowedRoutes(AuthUser authUser) {
        if (authUser == null) {
            throw new BizException("UNAUTHORIZED", "Not logged in or session expired");
        }
        List<ProcurementApprovalRoute> routes = enabledRoutes();
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return routes;
        }
        return routes.stream()
            .filter(route -> matchesUser(route, authUser.getDeptCode(), authUser.getDeptName()))
            .toList();
    }

    public DepartmentSelection resolveSelection(AuthUser operator,
                                                Long applicantUserId,
                                                String deptCode,
                                                String deptName,
                                                String actingReason) {
        if (operator == null) {
            throw new BizException("UNAUTHORIZED", "Not logged in or session expired");
        }
        boolean systemAdmin = DataScopeHelper.isSystemAdmin(operator);
        SysUser applicant = resolveApplicant(operator, applicantUserId, systemAdmin);
        List<ProcurementApprovalRoute> allRoutes = enabledRoutes();
        if (allRoutes.isEmpty()) {
            throw new BizException("PROCUREMENT_ROUTE_EMPTY", "No enabled procurement department route is configured");
        }

        ProcurementApprovalRoute selectedRoute = matchRoute(allRoutes, deptCode, deptName);
        if (selectedRoute == null && !StringUtils.hasText(deptCode) && !StringUtils.hasText(deptName)) {
            selectedRoute = matchRoute(allRoutes, applicant.getDeptCode(), applicant.getDeptName());
        }
        if (selectedRoute == null) {
            throw new BizException("PROCUREMENT_ROUTE_NOT_FOUND", "Selected procurement department is not bound to an enabled approval route");
        }

        if (!systemAdmin) {
            if (!Objects.equals(operator.getUserId(), applicant.getId())) {
                throw new BizException("FORBIDDEN", "Only administrators can submit procurement requests for another user");
            }
            Long selectedRouteId = selectedRoute.getId();
            boolean allowed = listAllowedRoutes(operator).stream()
                .anyMatch(route -> Objects.equals(route.getId(), selectedRouteId));
            if (!allowed) {
                throw new BizException("FORBIDDEN", "Only your own procurement department can be selected");
            }
            return new DepartmentSelection(applicant, selectedRoute, false, null, null, null);
        }

        boolean acting = !Objects.equals(operator.getUserId(), applicant.getId())
            || !matchesUser(selectedRoute, applicant.getDeptCode(), applicant.getDeptName());
        if (!acting) {
            return new DepartmentSelection(applicant, selectedRoute, false, null, null, null);
        }
        return new DepartmentSelection(
            applicant,
            selectedRoute,
            true,
            operator.getUserId(),
            displayName(operator),
            StringUtils.hasText(actingReason) ? actingReason.trim() : DEFAULT_ACTING_REASON
        );
    }

    private SysUser resolveApplicant(AuthUser operator, Long applicantUserId, boolean systemAdmin) {
        Long effectiveApplicantId = applicantUserId == null ? operator.getUserId() : applicantUserId;
        if (!systemAdmin && !Objects.equals(operator.getUserId(), effectiveApplicantId)) {
            throw new BizException("FORBIDDEN", "Only administrators can submit procurement requests for another user");
        }
        SysUser applicant = userMapper.selectById(effectiveApplicantId);
        if (applicant == null || applicant.getStatus() == null || applicant.getStatus() != 1) {
            throw new BizException("APPLICANT_NOT_FOUND", "Applicant user is not active");
        }
        if (DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(applicant.getUserType())) {
            throw new BizException("APPLICANT_INVALID", "Supplier accounts cannot submit internal procurement requests");
        }
        return applicant;
    }

    private List<ProcurementApprovalRoute> enabledRoutes() {
        routeService.listEnabledRoutes();
        return routeMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalRoute>()
                .eq(ProcurementApprovalRoute::getEnabled, 1)
                .orderByAsc(ProcurementApprovalRoute::getRegionName)
                .orderByAsc(ProcurementApprovalRoute::getRoomName)
                .orderByAsc(ProcurementApprovalRoute::getBuildingName));
    }

    private ProcurementApprovalRoute matchRoute(List<ProcurementApprovalRoute> routes, String deptCode, String deptName) {
        String normalizedDeptCode = normalized(deptCode);
        String normalizedDeptName = normalized(deptName);
        if (StringUtils.hasText(normalizedDeptCode)) {
            for (ProcurementApprovalRoute route : routes) {
                if (normalizedDeptCode.equals(normalized(route.getDeptCode()))
                    || normalizedDeptCode.equals(normalized(route.getCostCenterCode()))) {
                    return route;
                }
            }
        }
        if (StringUtils.hasText(normalizedDeptName)) {
            for (ProcurementApprovalRoute route : routes) {
                if (normalizedDeptName.equals(normalized(route.getDeptName()))) {
                    return route;
                }
            }
            ProcurementApprovalRoute route = uniqueLongestContaining(routes, normalizedDeptName, ProcurementApprovalRoute::getCostCenterCode);
            if (route != null) {
                return route;
            }
            route = uniqueLongestContaining(routes, normalizedDeptName, ProcurementApprovalRoute::getBuildingName);
            if (route != null) {
                return route;
            }
            route = uniqueLongestContaining(routes, normalizedDeptName, ProcurementApprovalRoute::getRoomName);
            if (route != null) {
                return route;
            }
        }
        return null;
    }

    private boolean matchesUser(ProcurementApprovalRoute route, String userDeptCode, String userDeptName) {
        if (route == null) {
            return false;
        }
        return matchRoute(List.of(route), userDeptCode, userDeptName) != null;
    }

    private ProcurementApprovalRoute uniqueLongestContaining(List<ProcurementApprovalRoute> routes,
                                                             String source,
                                                             Function<ProcurementApprovalRoute, String> targetGetter) {
        List<ProcurementApprovalRoute> matches = routes.stream()
            .filter(route -> containsText(source, targetGetter.apply(route)))
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
        List<ProcurementApprovalRoute> bestMatches = matches.stream()
            .filter(route -> normalized(targetGetter.apply(route)).length() == bestLength)
            .sorted(Comparator.comparing(route -> normalized(targetGetter.apply(route))))
            .toList();
        return bestMatches.size() == 1 ? bestMatches.get(0) : null;
    }

    private boolean containsText(String source, String target) {
        return StringUtils.hasText(source) && StringUtils.hasText(target) && source.contains(normalized(target));
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private String displayName(AuthUser user) {
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return String.valueOf(user.getUserId());
    }

    public record DepartmentSelection(
        SysUser applicant,
        ProcurementApprovalRoute route,
        boolean acting,
        Long actingUserId,
        String actingUserName,
        String actingReason
    ) {
    }
}
