package com.company.procurement.modules.system.department.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.org.scope.entity.OrgScope;
import com.company.procurement.modules.org.scope.mapper.OrgScopeMapper;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.mapper.ProcurementApprovalRouteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserDepartmentResolver {

    private static final int MIN_PARTIAL_NAME_LENGTH = 6;

    private final ProcurementApprovalRouteMapper routeMapper;
    private final OrgScopeMapper orgScopeMapper;
    private final JdbcTemplate jdbcTemplate;

    public DepartmentReferences loadReferences() {
        List<ProcurementApprovalRoute> routes = routeMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalRoute>()
            .eq(ProcurementApprovalRoute::getEnabled, 1)
            .orderByAsc(ProcurementApprovalRoute::getRegionName)
            .orderByAsc(ProcurementApprovalRoute::getRoomName)
            .orderByAsc(ProcurementApprovalRoute::getBuildingName));
        List<OrgScope> scopes = orgScopeMapper.selectList(new LambdaQueryWrapper<OrgScope>()
            .eq(OrgScope::getStatus, 1)
            .orderByAsc(OrgScope::getRegionName)
            .orderByAsc(OrgScope::getRoomName)
            .orderByAsc(OrgScope::getBuildingName));
        return new DepartmentReferences(routes, scopes, loadActiveDepartments());
    }

    public DepartmentFields resolve(String deptCode, String deptName) {
        return resolve(deptCode, deptName, loadReferences());
    }

    public DepartmentFields resolve(String deptCode, String deptName, DepartmentReferences references) {
        String originalCode = trimToNull(deptCode);
        String originalName = trimToNull(deptName);
        if (!StringUtils.hasText(originalCode) && !StringUtils.hasText(originalName)) {
            return new DepartmentFields(null, null);
        }

        ProcurementApprovalRoute route = matchRoute(originalCode, originalName, references.routes());
        if (route != null) {
            return fields(route);
        }

        OrgScope scope = matchScope(originalCode, originalName, references.scopes());
        if (scope != null) {
            route = matchRoute(scopeCode(scope), scopeName(scope), references.routes());
            return route == null ? fields(scope) : fields(route);
        }

        DepartmentCandidate department = matchDepartment(originalCode, originalName, references.departments());
        if (department != null) {
            route = matchRoute(department.code(), firstText(department.fullName(), department.shortName()), references.routes());
            if (route != null) {
                return fields(route);
            }
            scope = matchScope(department.code(), firstText(department.fullName(), department.shortName()), references.scopes());
            if (scope != null) {
                return fields(scope);
            }
            return new DepartmentFields(trimToNull(department.code()), trimToNull(department.fullName()));
        }

        return new DepartmentFields(originalCode, originalName);
    }

    private List<DepartmentCandidate> loadActiveDepartments() {
        if (!departmentTableExists()) {
            return List.of();
        }
        try {
            return jdbcTemplate.query("""
                SELECT code, full_name, short_name, dept_type
                FROM sys_department
                WHERE is_deleted = 0
                  AND COALESCE(is_active, 1) = 1
                  AND code IS NOT NULL
                  AND code <> ''
                ORDER BY
                  CASE dept_type
                    WHEN 'BUILDING' THEN 1
                    WHEN 'PARK' THEN 2
                    WHEN 'REGION' THEN 3
                    WHEN 'HEADQUARTER' THEN 4
                    ELSE 5
                  END,
                  sort_order,
                  id
                """, (rs, rowNum) -> new DepartmentCandidate(
                rs.getString("code"),
                rs.getString("full_name"),
                rs.getString("short_name"),
                rs.getString("dept_type")
            ));
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private boolean departmentTableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'sys_department'
                """, Integer.class);
            return count != null && count > 0;
        } catch (DataAccessException ignored) {
            return false;
        }
    }

    private ProcurementApprovalRoute matchRoute(String deptCode, String deptName, List<ProcurementApprovalRoute> routes) {
        List<Match<ProcurementApprovalRoute>> matches = new ArrayList<>();
        for (ProcurementApprovalRoute route : safeList(routes)) {
            addExact(matches, route, deptCode, route.getDeptCode(), 1);
            addExact(matches, route, deptCode, route.getCostCenterCode(), 1);
            addExact(matches, route, deptCode, route.getRouteKey(), 2);
            addExact(matches, route, deptName, route.getDeptName(), 3);
            addExact(matches, route, deptName, route.getBuildingName(), 4);
            addContains(matches, route, deptName, route.getDeptName(), 5, true);
            addContains(matches, route, deptName, route.getCostCenterCode(), 6, false);
            addContains(matches, route, deptName, route.getBuildingName(), 7, false);
            addContains(matches, route, deptName, route.getRoomName(), 8, false);
        }
        return uniqueBest(matches);
    }

    private OrgScope matchScope(String deptCode, String deptName, List<OrgScope> scopes) {
        List<Match<OrgScope>> matches = new ArrayList<>();
        for (OrgScope scope : safeList(scopes)) {
            addExact(matches, scope, deptCode, scope.getDeptCode(), 1);
            addExact(matches, scope, deptCode, scope.getCostCenterCode(), 1);
            addExact(matches, scope, deptCode, scope.getScopeCode(), 2);
            addExact(matches, scope, deptName, scope.getDeptName(), 3);
            addExact(matches, scope, deptName, scope.getBuildingName(), 4);
            addContains(matches, scope, deptName, scope.getDeptName(), 5, true);
            addContains(matches, scope, deptName, scope.getCostCenterCode(), 6, false);
            addContains(matches, scope, deptName, scope.getBuildingName(), 7, false);
            addContains(matches, scope, deptName, scope.getRoomName(), 8, false);
        }
        return uniqueBest(matches);
    }

    private DepartmentCandidate matchDepartment(String deptCode, String deptName, List<DepartmentCandidate> departments) {
        List<Match<DepartmentCandidate>> matches = new ArrayList<>();
        for (DepartmentCandidate department : safeList(departments)) {
            addExact(matches, department, deptCode, department.code(), 1);
            addExact(matches, department, deptName, department.fullName(), 2);
            addExact(matches, department, deptName, department.shortName(), 3);
            addContains(matches, department, deptName, department.fullName(), 4, true);
            addContains(matches, department, deptName, department.shortName(), 5, false);
        }
        return uniqueBest(matches);
    }

    private <T> void addExact(List<Match<T>> matches, T target, String source, String value, int priority) {
        if (StringUtils.hasText(source) && StringUtils.hasText(value) && normalized(source).equals(normalized(value))) {
            matches.add(new Match<>(target, priority, normalized(value).length()));
        }
    }

    private <T> void addContains(List<Match<T>> matches, T target, String source, String value, int priority, boolean allowReverse) {
        String normalizedSource = normalized(source);
        String normalizedValue = normalized(value);
        if (!StringUtils.hasText(normalizedSource) || !StringUtils.hasText(normalizedValue)) {
            return;
        }
        if (normalizedSource.contains(normalizedValue)) {
            matches.add(new Match<>(target, priority, normalizedValue.length()));
        } else if (allowReverse && normalizedSource.length() >= MIN_PARTIAL_NAME_LENGTH && normalizedValue.contains(normalizedSource)) {
            matches.add(new Match<>(target, priority + 1, normalizedSource.length()));
        }
    }

    private <T> T uniqueBest(List<Match<T>> matches) {
        if (matches.isEmpty()) {
            return null;
        }
        Match<T> best = matches.stream()
            .min(Comparator.comparingInt(Match<T>::priority).thenComparing(Comparator.comparingInt(Match<T>::specificity).reversed()))
            .orElse(null);
        if (best == null) {
            return null;
        }
        long bestCount = matches.stream()
            .filter(match -> match.priority() == best.priority() && match.specificity() == best.specificity())
            .map(Match::target)
            .distinct()
            .count();
        return bestCount == 1 ? best.target() : null;
    }

    private DepartmentFields fields(ProcurementApprovalRoute route) {
        return new DepartmentFields(
            firstText(route.getDeptCode(), route.getCostCenterCode(), route.getRouteKey()),
            firstText(route.getDeptName(), joinName(route.getRoomName(), route.getBuildingName()))
        );
    }

    private DepartmentFields fields(OrgScope scope) {
        return new DepartmentFields(scopeCode(scope), scopeName(scope));
    }

    private String scopeCode(OrgScope scope) {
        return firstText(scope.getDeptCode(), scope.getCostCenterCode(), scope.getScopeCode());
    }

    private String scopeName(OrgScope scope) {
        return firstText(scope.getDeptName(), scope.getScopeName(), joinName(scope.getRoomName(), scope.getBuildingName()));
    }

    private String joinName(String left, String right) {
        if (StringUtils.hasText(left) && StringUtils.hasText(right)) {
            return left.trim() + "-" + right.trim();
        }
        return firstText(left, right);
    }

    private String firstText(String... values) {
        for (String value : values) {
            String text = trimToNull(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record DepartmentReferences(
        List<ProcurementApprovalRoute> routes,
        List<OrgScope> scopes,
        List<DepartmentCandidate> departments
    ) {
    }

    public record DepartmentFields(String deptCode, String deptName) {
    }

    public record DepartmentCandidate(String code, String fullName, String shortName, String deptType) {
    }

    private record Match<T>(T target, int priority, int specificity) {
    }
}
