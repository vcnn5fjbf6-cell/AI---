package com.company.procurement.modules.procurement.bi.service.impl;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.bi.dto.ProcurementBiDashboardSaveDTO;
import com.company.procurement.modules.procurement.bi.dto.ProcurementBiQueryDTO;
import com.company.procurement.modules.procurement.bi.service.ProcurementBiService;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiDashboardVO;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiDatasetVO;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiQueryResultVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProcurementBiServiceImpl implements ProcurementBiService {

    private static final String DASHBOARD_CODE = "procurement_board";
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, DatasetSpec> datasets = buildDatasets();

    @Override
    public List<ProcurementBiDatasetVO> listDatasets() {
        AuthContextHolder.requireUser();
        return datasets.values().stream().map(this::toDatasetVO).toList();
    }

    @Override
    public List<ProcurementBiDatasetVO.FieldVO> listFields(String datasetCode) {
        AuthContextHolder.requireUser();
        return toDatasetVO(requireDataset(datasetCode)).getFields();
    }

    @Override
    public ProcurementBiQueryResultVO query(ProcurementBiQueryDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        if (dto == null || !StringUtils.hasText(dto.getDatasetCode())) {
            throw new BizException("BI_DATASET_REQUIRED", "请选择数据源");
        }
        DatasetSpec dataset = requireDataset(dto.getDatasetCode());
        if (Boolean.TRUE.equals(dto.getDetail())) {
            return queryDetail(dataset, dto, authUser);
        }
        return queryAggregate(dataset, dto, authUser);
    }

    @Override
    public ProcurementBiDashboardVO getDefaultDashboard() {
        AuthUser user = AuthContextHolder.requireUser();
        ProcurementBiDashboardVO vo = defaultDashboard();
        try {
            String json = jdbcTemplate.query("""
                    SELECT layout_schema
                    FROM bi_dashboard
                    WHERE dashboard_code = ? AND owner_user_id = ? AND is_deleted = 0 AND status = 'ACTIVE'
                    ORDER BY updated_at DESC
                    LIMIT 1
                    """, rs -> rs.next() ? rs.getString("layout_schema") : null, DASHBOARD_CODE, user.getUserId());
            if (StringUtils.hasText(json)) {
                vo.setLayout(objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
                }));
            }
        } catch (Exception ignored) {
            // The dashboard still works with the built-in layout when BI metadata tables are not initialized locally.
        }
        return vo;
    }

    @Override
    public ProcurementBiDashboardVO saveDefaultDashboard(ProcurementBiDashboardSaveDTO dto) {
        AuthUser user = AuthContextHolder.requireUser();
        Map<String, Object> layout = dto == null || dto.getLayout() == null ? new LinkedHashMap<>() : dto.getLayout();
        try {
            String json = objectMapper.writeValueAsString(layout);
            jdbcTemplate.update("""
                    INSERT INTO bi_dashboard
                    (dashboard_code, dashboard_name, route_path, owner_user_id, layout_schema, description, status, created_by, updated_by, is_deleted, version)
                    VALUES (?, '采购看板', '/procurement/dashboard', ?, ?, '用户自定义采购看板布局', 'ACTIVE', ?, ?, 0, 0)
                    ON DUPLICATE KEY UPDATE
                      layout_schema = VALUES(layout_schema),
                      dashboard_name = VALUES(dashboard_name),
                      route_path = VALUES(route_path),
                      description = VALUES(description),
                      status = VALUES(status),
                      updated_by = VALUES(updated_by)
                    """, DASHBOARD_CODE, user.getUserId(), json, user.getUserId(), user.getUserId());
        } catch (Exception ex) {
            throw new BizException("BI_DASHBOARD_SAVE_FAILED", "看板保存失败，请确认BI基础表已初始化");
        }
        ProcurementBiDashboardVO vo = defaultDashboard();
        vo.setLayout(layout);
        return vo;
    }

    private ProcurementBiQueryResultVO queryDetail(DatasetSpec dataset, ProcurementBiQueryDTO dto, AuthUser authUser) {
        List<FieldSpec> fields = resolveFields(dataset, dto.getFields());
        if (fields.isEmpty()) {
            fields = dataset.defaultDetailFields().stream().map(dataset.fields::get).filter(Objects::nonNull).toList();
        }
        if (fields.isEmpty()) {
            throw new BizException("BI_FIELD_REQUIRED", "请选择字段");
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> selectParts = new ArrayList<>();
        for (FieldSpec field : fields) {
            selectParts.add(field.column() + " AS `" + field.code() + "`");
        }
        sql.append(String.join(", ", selectParts));
        sql.append(" FROM (").append(dataset.baseSql()).append(") bi_base WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        params.addAll(appendVisibilityScope(sql, authUser));
        params.addAll(appendFilters(sql, dataset, dto.getFilters()));
        appendSort(sql, dataset, dto.getSorts(), fields);
        sql.append(" LIMIT ").append(normalizeLimit(dto.getLimit()));

        ProcurementBiQueryResultVO result = new ProcurementBiQueryResultVO();
        result.setDatasetCode(dataset.code());
        for (FieldSpec field : fields) {
            result.getColumns().add(toColumnVO(field, field.code(), null));
        }
        result.setRows(jdbcTemplate.queryForList(sql.toString(), params.toArray()));
        result.getSummary().put("rowCount", result.getRows().size());
        return result;
    }

    private ProcurementBiQueryResultVO queryAggregate(DatasetSpec dataset, ProcurementBiQueryDTO dto, AuthUser authUser) {
        List<FieldSpec> dimensions = resolveFields(dataset, dto.getDimensions()).stream()
            .filter(FieldSpec::groupable)
            .toList();
        List<MetricSelection> metrics = resolveMetrics(dataset, dto.getMetrics());
        if (metrics.isEmpty()) {
            throw new BizException("BI_METRIC_REQUIRED", "请选择指标");
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> selectParts = new ArrayList<>();
        for (FieldSpec field : dimensions) {
            selectParts.add(field.column() + " AS `" + field.code() + "`");
        }
        for (MetricSelection metric : metrics) {
            selectParts.add(metric.sql() + " AS `" + metric.outputCode() + "`");
        }
        sql.append(String.join(", ", selectParts));
        sql.append(" FROM (").append(dataset.baseSql()).append(") bi_base WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        params.addAll(appendVisibilityScope(sql, authUser));
        params.addAll(appendFilters(sql, dataset, dto.getFilters()));
        if (!dimensions.isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(String.join(", ", dimensions.stream().map(FieldSpec::column).toList()));
        }
        appendAggregateSort(sql, dimensions, metrics, dto.getSorts());
        sql.append(" LIMIT ").append(normalizeLimit(dto.getLimit()));

        ProcurementBiQueryResultVO result = new ProcurementBiQueryResultVO();
        result.setDatasetCode(dataset.code());
        for (FieldSpec field : dimensions) {
            result.getColumns().add(toColumnVO(field, field.code(), null));
        }
        for (MetricSelection metric : metrics) {
            result.getColumns().add(toColumnVO(metric.field(), metric.outputCode(), metric.aggregation()));
        }
        result.setRows(jdbcTemplate.queryForList(sql.toString(), params.toArray()));
        result.getSummary().put("rowCount", result.getRows().size());
        return result;
    }

    private List<Object> appendVisibilityScope(StringBuilder sql, AuthUser authUser) {
        List<Object> params = new ArrayList<>();
        if (DataScopeHelper.isSuperAdmin(authUser)) {
            return params;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            if (authUser == null || authUser.getSupplierId() == null) {
                sql.append(" AND 1 = 0");
                return params;
            }
            sql.append(" AND bi_base.supplierId = ?");
            params.add(authUser.getSupplierId());
            return params;
        }

        Long userId = authUser == null ? null : authUser.getUserId();
        if (userId == null) {
            sql.append(" AND 1 = 0");
            return params;
        }

        sql.append(" AND (");
        appendOwnDepartmentScope(sql, params, authUser);
        appendAuthorizedRoomScope(sql, params, authUser);
        sql.append(")");
        return params;
    }

    private void appendOwnDepartmentScope(StringBuilder sql, List<Object> params, AuthUser authUser) {
        List<String> conditions = new ArrayList<>();
        if (authUser != null && StringUtils.hasText(authUser.getDeptCode())) {
            conditions.add("(bi_base.deptCode IS NOT NULL AND bi_base.deptCode <> '' AND TRIM(bi_base.deptCode) = ?)");
            params.add(authUser.getDeptCode().trim());
        }
        if (authUser != null && StringUtils.hasText(authUser.getDeptName())) {
            conditions.add("(bi_base.deptName IS NOT NULL AND bi_base.deptName <> '' AND TRIM(bi_base.deptName) = ?)");
            params.add(authUser.getDeptName().trim());
        }
        if (conditions.isEmpty()) {
            sql.append("1 = 0");
            return;
        }
        sql.append("(").append(String.join(" OR ", conditions)).append(")");
    }

    private void appendAuthorizedRoomScope(StringBuilder sql, List<Object> params, AuthUser authUser) {
        if (authUser == null || authUser.getUserId() == null) {
            return;
        }
        sql.append("""
               OR EXISTS (
                 SELECT 1
                 FROM org_scope scope_room
                 JOIN procurement_approval_policy scope_policy
                   ON scope_policy.scope_id = scope_room.id
                  AND scope_policy.enabled = 1
                  AND scope_policy.is_deleted = 0
                 JOIN procurement_approval_level scope_level
                   ON scope_level.policy_id = scope_policy.id
                  AND scope_level.enabled = 1
                  AND scope_level.is_deleted = 0
                 JOIN procurement_approval_level_user scope_user
                   ON scope_user.level_id = scope_level.id
                  AND scope_user.status = 1
                  AND scope_user.is_deleted = 0
                 WHERE scope_user.user_id = ?
                   AND scope_room.status = 1
                   AND scope_room.is_deleted = 0
                   AND (
                     (bi_base.deptCode IS NOT NULL AND bi_base.deptCode <> ''
                       AND (scope_room.dept_code = bi_base.deptCode OR scope_room.cost_center_code = bi_base.deptCode))
                     OR (bi_base.deptName IS NOT NULL AND bi_base.deptName <> ''
                       AND (
                         scope_room.dept_name = bi_base.deptName
                         OR (scope_room.cost_center_code IS NOT NULL AND scope_room.cost_center_code <> ''
                           AND INSTR(bi_base.deptName, scope_room.cost_center_code) > 0)
                         OR (scope_room.building_name IS NOT NULL AND scope_room.building_name <> ''
                           AND scope_room.room_name IS NOT NULL AND scope_room.room_name <> ''
                           AND INSTR(bi_base.deptName, scope_room.building_name) > 0
                           AND INSTR(bi_base.deptName, scope_room.room_name) > 0)
                       ))
                   )
               )
            """);
        params.add(authUser.getUserId());

        List<String> identifiers = userIdentifiers(authUser);
        if (identifiers.isEmpty()) {
            return;
        }
        sql.append("""
               OR EXISTS (
                 SELECT 1
                 FROM procurement_approval_route scope_route
                 WHERE scope_route.enabled = 1
                   AND scope_route.is_deleted = 0
                   AND (
                     (bi_base.deptCode IS NOT NULL AND bi_base.deptCode <> ''
                       AND (scope_route.dept_code = bi_base.deptCode OR scope_route.cost_center_code = bi_base.deptCode))
                     OR (bi_base.deptName IS NOT NULL AND bi_base.deptName <> ''
                       AND (
                         scope_route.dept_name = bi_base.deptName
                         OR (scope_route.cost_center_code IS NOT NULL AND scope_route.cost_center_code <> ''
                           AND INSTR(bi_base.deptName, scope_route.cost_center_code) > 0)
                         OR (scope_route.building_name IS NOT NULL AND scope_route.building_name <> ''
                           AND scope_route.room_name IS NOT NULL AND scope_route.room_name <> ''
                           AND INSTR(bi_base.deptName, scope_route.building_name) > 0
                           AND INSTR(bi_base.deptName, scope_route.room_name) > 0)
                       ))
                   )
                   AND (
            """);
        List<String> columns = List.of(
            "floor_leader_names",
            "manager_names",
            "region_manager_names",
            "director_names",
            "replacement_confirm_names"
        );
        List<String> nameConditions = new ArrayList<>();
        for (String column : columns) {
            nameConditions.add(nameListContainsAnySql(column, identifiers, params));
        }
        sql.append(String.join(" OR ", nameConditions));
        sql.append("""
                   )
               )
            """);
    }

    private List<String> userIdentifiers(AuthUser authUser) {
        Set<String> identifiers = new LinkedHashSet<>();
        if (authUser != null) {
            addIdentifier(identifiers, authUser.getRealName());
            addIdentifier(identifiers, authUser.getUsername());
            addIdentifier(identifiers, authUser.getEmployeeNo());
        }
        return new ArrayList<>(identifiers);
    }

    private void addIdentifier(Set<String> identifiers, String value) {
        if (StringUtils.hasText(value)) {
            identifiers.add(value.trim());
        }
    }

    private String nameListContainsAnySql(String column, List<String> identifiers, List<Object> params) {
        List<String> parts = new ArrayList<>();
        String normalized = "REPLACE(REPLACE(COALESCE(scope_route." + column + ", ''), CONVERT(0xEFBC8C USING utf8mb4), ','), CONVERT(0xE38081 USING utf8mb4), ',')";
        for (String identifier : identifiers) {
            parts.add("FIND_IN_SET(?, " + normalized + ") > 0");
            params.add(identifier);
        }
        return "(" + String.join(" OR ", parts) + ")";
    }

    private List<Object> appendFilters(StringBuilder sql, DatasetSpec dataset, List<ProcurementBiQueryDTO.FilterDTO> filters) {
        if (CollectionUtils.isEmpty(filters)) {
            return new ArrayList<>();
        }
        List<Object> params = new ArrayList<>();
        for (ProcurementBiQueryDTO.FilterDTO filter : filters) {
            if (filter == null || !StringUtils.hasText(filter.getFieldCode())) {
                continue;
            }
            FieldSpec field = dataset.fields().get(filter.getFieldCode());
            if (field == null || !field.filterable()) {
                continue;
            }
            String operator = normalizeOperator(filter.getOperator());
            if ("IN".equals(operator)) {
                List<Object> values = normalizeValues(filter);
                if (values.isEmpty()) {
                    continue;
                }
                sql.append(" AND ").append(field.column()).append(" IN (")
                    .append(String.join(", ", Collections.nCopies(values.size(), "?")))
                    .append(")");
                params.addAll(values);
                continue;
            }
            if ("BETWEEN".equals(operator)) {
                List<Object> values = normalizeValues(filter);
                if (values.size() < 2) {
                    continue;
                }
                sql.append(" AND ").append(field.column()).append(" BETWEEN ? AND ?");
                params.add(coerceFilterValue(field, values.get(0), true));
                params.add(coerceFilterValue(field, values.get(1), false));
                continue;
            }
            Object value = filter.getValue();
            if (value == null || (value instanceof String text && !StringUtils.hasText(text))) {
                continue;
            }
            if ("LIKE".equals(operator)) {
                sql.append(" AND ").append(field.column()).append(" LIKE ?");
                params.add("%" + value + "%");
            } else {
                sql.append(" AND ").append(field.column()).append(" ").append(operator).append(" ?");
                params.add(coerceFilterValue(field, value, true));
            }
        }
        return params;
    }

    private void appendSort(StringBuilder sql, DatasetSpec dataset, List<ProcurementBiQueryDTO.SortDTO> sorts, List<FieldSpec> selectedFields) {
        List<String> orderParts = new ArrayList<>();
        Set<String> selectedCodes = selectedFields.stream().map(FieldSpec::code).collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (!CollectionUtils.isEmpty(sorts)) {
            for (ProcurementBiQueryDTO.SortDTO sort : sorts) {
                FieldSpec field = sort == null ? null : dataset.fields().get(sort.getFieldCode());
                if (field != null && field.sortable() && selectedCodes.contains(field.code())) {
                    orderParts.add("`" + field.code() + "` " + normalizeDirection(sort.getDirection()));
                }
            }
        }
        if (orderParts.isEmpty() && selectedCodes.contains("submittedAt")) {
            orderParts.add("`submittedAt` DESC");
        } else if (orderParts.isEmpty() && selectedCodes.contains("createdAt")) {
            orderParts.add("`createdAt` DESC");
        }
        if (!orderParts.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", orderParts));
        }
    }

    private void appendAggregateSort(StringBuilder sql,
                                     List<FieldSpec> dimensions,
                                     List<MetricSelection> metrics,
                                     List<ProcurementBiQueryDTO.SortDTO> sorts) {
        List<String> orderParts = new ArrayList<>();
        Set<String> dimensionCodes = dimensions.stream().map(FieldSpec::code).collect(LinkedHashSet::new, Set::add, Set::addAll);
        Map<String, String> metricOutputs = new HashMap<>();
        for (MetricSelection metric : metrics) {
            metricOutputs.put(metric.field().code(), metric.outputCode());
            metricOutputs.put(metric.outputCode(), metric.outputCode());
        }
        if (!CollectionUtils.isEmpty(sorts)) {
            for (ProcurementBiQueryDTO.SortDTO sort : sorts) {
                if (sort == null || !StringUtils.hasText(sort.getFieldCode())) {
                    continue;
                }
                String code = sort.getFieldCode();
                if (dimensionCodes.contains(code)) {
                    orderParts.add("`" + code + "` " + normalizeDirection(sort.getDirection()));
                } else if (metricOutputs.containsKey(code)) {
                    orderParts.add("`" + metricOutputs.get(code) + "` " + normalizeDirection(sort.getDirection()));
                }
            }
        }
        if (orderParts.isEmpty() && !metrics.isEmpty()) {
            orderParts.add("`" + metrics.get(0).outputCode() + "` DESC");
        }
        if (!orderParts.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", orderParts));
        }
    }

    private List<FieldSpec> resolveFields(DatasetSpec dataset, List<String> codes) {
        if (CollectionUtils.isEmpty(codes)) {
            return new ArrayList<>();
        }
        List<FieldSpec> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String code : codes) {
            FieldSpec field = dataset.fields().get(code);
            if (field != null && seen.add(field.code())) {
                fields.add(field);
            }
        }
        return fields;
    }

    private List<MetricSelection> resolveMetrics(DatasetSpec dataset, List<ProcurementBiQueryDTO.MetricDTO> metricDTOs) {
        if (CollectionUtils.isEmpty(metricDTOs)) {
            return new ArrayList<>();
        }
        List<MetricSelection> metrics = new ArrayList<>();
        for (ProcurementBiQueryDTO.MetricDTO dto : metricDTOs) {
            if (dto == null || !StringUtils.hasText(dto.getFieldCode())) {
                continue;
            }
            FieldSpec field = dataset.fields().get(dto.getFieldCode());
            if (field == null || !"MEASURE".equals(field.role())) {
                continue;
            }
            String aggregation = normalizeAggregation(StringUtils.hasText(dto.getAggregation()) ? dto.getAggregation() : field.defaultAggregation());
            String outputCode = field.code() + "__" + aggregation.toLowerCase(Locale.ROOT);
            metrics.add(new MetricSelection(field, aggregation, outputCode, aggregationSql(field, aggregation)));
        }
        return metrics;
    }

    private String aggregationSql(FieldSpec field, String aggregation) {
        return switch (aggregation) {
            case "AVG" -> "AVG(" + field.column() + ")";
            case "MIN" -> "MIN(" + field.column() + ")";
            case "MAX" -> "MAX(" + field.column() + ")";
            case "COUNT" -> "COUNT(" + field.column() + ")";
            case "COUNT_DISTINCT" -> "COUNT(DISTINCT " + field.column() + ")";
            default -> "SUM(COALESCE(" + field.column() + ", 0))";
        };
    }

    private ProcurementBiQueryResultVO.ColumnVO toColumnVO(FieldSpec field, String code, String aggregation) {
        ProcurementBiQueryResultVO.ColumnVO vo = new ProcurementBiQueryResultVO.ColumnVO();
        vo.setCode(code);
        vo.setLabel(aggregation == null ? field.name() : field.name() + aggregationName(aggregation));
        vo.setRole(field.role());
        vo.setDataType(field.dataType());
        vo.setSemanticType(field.semanticType());
        vo.setUnit(field.unit());
        vo.setAggregation(aggregation);
        return vo;
    }

    private ProcurementBiDatasetVO toDatasetVO(DatasetSpec dataset) {
        ProcurementBiDatasetVO vo = new ProcurementBiDatasetVO();
        vo.setDatasetCode(dataset.code());
        vo.setDatasetName(dataset.name());
        vo.setDescription(dataset.description());
        for (FieldSpec field : dataset.fields().values()) {
            ProcurementBiDatasetVO.FieldVO fieldVO = new ProcurementBiDatasetVO.FieldVO();
            fieldVO.setFieldCode(field.code());
            fieldVO.setFieldName(field.name());
            fieldVO.setDataType(field.dataType());
            fieldVO.setSemanticType(field.semanticType());
            fieldVO.setRole(field.role());
            fieldVO.setUnit(field.unit());
            fieldVO.setDefaultAggregation(field.defaultAggregation());
            fieldVO.setFilterable(field.filterable());
            fieldVO.setGroupable(field.groupable());
            fieldVO.setSortable(field.sortable());
            vo.getFields().add(fieldVO);
        }
        return vo;
    }

    private DatasetSpec requireDataset(String datasetCode) {
        DatasetSpec dataset = datasets.get(datasetCode);
        if (dataset == null) {
            throw new BizException("BI_DATASET_NOT_FOUND", "数据源不存在");
        }
        return dataset;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeAggregation(String aggregation) {
        String value = StringUtils.hasText(aggregation) ? aggregation.trim().toUpperCase(Locale.ROOT) : "SUM";
        return switch (value) {
            case "AVG", "MIN", "MAX", "COUNT", "COUNT_DISTINCT" -> value;
            default -> "SUM";
        };
    }

    private String normalizeOperator(String operator) {
        String value = StringUtils.hasText(operator) ? operator.trim().toUpperCase(Locale.ROOT) : "=";
        return switch (value) {
            case "=", "!=", ">", ">=", "<", "<=", "LIKE", "IN", "BETWEEN" -> value;
            case "EQ" -> "=";
            case "NE" -> "!=";
            case "GT" -> ">";
            case "GTE" -> ">=";
            case "LT" -> "<";
            case "LTE" -> "<=";
            default -> "=";
        };
    }

    private String normalizeDirection(String direction) {
        return "ASC".equalsIgnoreCase(direction) ? "ASC" : "DESC";
    }

    private List<Object> normalizeValues(ProcurementBiQueryDTO.FilterDTO filter) {
        if (!CollectionUtils.isEmpty(filter.getValues())) {
            return filter.getValues();
        }
        Object value = filter.getValue();
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return value == null ? new ArrayList<>() : List.of(value);
    }

    private Object coerceFilterValue(FieldSpec field, Object value, boolean startOfDay) {
        if (value == null) {
            return null;
        }
        if ("DATE".equals(field.dataType()) || "DATETIME".equals(field.dataType())) {
            if (value instanceof String text && StringUtils.hasText(text)) {
                String normalized = text.length() >= 10 ? text.substring(0, 10) : text;
                LocalDate date = LocalDate.parse(normalized);
                if ("DATE".equals(field.dataType())) {
                    return date;
                }
                return Timestamp.valueOf(LocalDateTime.of(date, startOfDay ? LocalTime.MIN : LocalTime.MAX));
            }
        }
        return value;
    }

    private String aggregationName(String aggregation) {
        return switch (aggregation) {
            case "AVG" -> "（平均）";
            case "MIN" -> "（最小）";
            case "MAX" -> "（最大）";
            case "COUNT" -> "（计数）";
            case "COUNT_DISTINCT" -> "（去重数）";
            default -> "（求和）";
        };
    }

    private ProcurementBiDashboardVO defaultDashboard() {
        ProcurementBiDashboardVO vo = new ProcurementBiDashboardVO();
        vo.setDashboardCode(DASHBOARD_CODE);
        vo.setDashboardName("采购看板");
        vo.setLayout(defaultLayout());
        return vo;
    }

    private Map<String, Object> defaultLayout() {
        List<Map<String, Object>> widgets = new ArrayList<>();
        widgets.add(metricWidget("w-demand-amount", "需求金额", "purchase_items", "requestAmount", "SUM", 0, 0));
        widgets.add(metricWidget("w-award-amount", "中标金额", "purchase_items", "awardAmount", "SUM", 2, 0));
        widgets.add(metricWidget("w-qty", "采购数量", "purchase_items", "qty", "SUM", 4, 0));
        widgets.add(metricWidget("w-suppliers", "成交供应商", "purchase_items", "supplierId", "COUNT_DISTINCT", 6, 0));
        widgets.add(metricWidget("w-lead", "平均交期", "purchase_items", "leadTimeDays", "AVG", 8, 0));
        widgets.add(chartWidget("w-dept", "机房金额排行", "bar", "purchase_items", List.of("roomName"), "requestAmount", "SUM", 0, 2, 5, 4));
        widgets.add(chartWidget("w-city", "收货城市占比", "pie", "purchase_items", List.of("receivingCity"), "requestAmount", "SUM", 5, 2, 3, 4));
        widgets.add(chartWidget("w-supplier", "供应商中标金额", "bar", "purchase_items", List.of("supplierName"), "awardAmount", "SUM", 8, 2, 4, 4));
        widgets.add(tableWidget("w-combo", "组合表", false, "purchase_items", List.of("roomName", "receivingCity"), List.of("requestAmount", "awardAmount"), 0, 6, 6, 5));
        widgets.add(detailWidget("w-detail", "采购明细", "purchase_items", List.of("reqNo", "itemName", "specModel", "roomName", "supplierName", "receivingCity", "qty", "requestAmount", "awardAmount", "requestStatus"), 6, 6, 6, 5));

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("version", 1);
        layout.put("columns", 12);
        layout.put("widgets", widgets);
        return layout;
    }

    private Map<String, Object> metricWidget(String id, String title, String dataset, String metric, String aggregation, int x, int y) {
        Map<String, Object> widget = baseWidget(id, title, "metric", dataset, x, y, 2, 2);
        widget.put("metrics", List.of(metricConfig(metric, aggregation)));
        widget.put("dimensions", List.of());
        return widget;
    }

    private Map<String, Object> chartWidget(String id, String title, String type, String dataset, List<String> dimensions,
                                            String metric, String aggregation, int x, int y, int w, int h) {
        Map<String, Object> widget = baseWidget(id, title, type, dataset, x, y, w, h);
        widget.put("dimensions", dimensions);
        widget.put("metrics", List.of(metricConfig(metric, aggregation)));
        widget.put("limit", 10);
        widget.put("showLegend", true);
        widget.put("showLabel", true);
        return widget;
    }

    private Map<String, Object> tableWidget(String id, String title, boolean detail, String dataset, List<String> dimensions,
                                            List<String> metrics, int x, int y, int w, int h) {
        Map<String, Object> widget = baseWidget(id, title, detail ? "detail" : "table", dataset, x, y, w, h);
        widget.put("detail", detail);
        widget.put("dimensions", dimensions);
        widget.put("metrics", metrics.stream().map(metric -> metricConfig(metric, "SUM")).toList());
        widget.put("limit", 100);
        return widget;
    }

    private Map<String, Object> detailWidget(String id, String title, String dataset, List<String> fields, int x, int y, int w, int h) {
        Map<String, Object> widget = tableWidget(id, title, true, dataset, List.of(), List.of(), x, y, w, h);
        widget.put("fields", fields);
        return widget;
    }

    private Map<String, Object> baseWidget(String id, String title, String type, String dataset, int x, int y, int w, int h) {
        Map<String, Object> widget = new LinkedHashMap<>();
        widget.put("id", id);
        widget.put("title", title);
        widget.put("type", type);
        widget.put("datasetCode", dataset);
        widget.put("x", x);
        widget.put("y", y);
        widget.put("w", w);
        widget.put("h", h);
        widget.put("visible", true);
        widget.put("theme", "default");
        return widget;
    }

    private Map<String, Object> metricConfig(String fieldCode, String aggregation) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("fieldCode", fieldCode);
        metric.put("aggregation", aggregation);
        return metric;
    }

    private Map<String, DatasetSpec> buildDatasets() {
        Map<String, DatasetSpec> map = new LinkedHashMap<>();
        map.put("purchase_items", purchaseItemsDataset());
        map.put("supplier_quote_items", supplierQuoteItemsDataset());
        map.put("receipt_items", receiptItemsDataset());
        map.put("after_sales", afterSalesDataset());
        map.put("supplier_quotes", supplierQuotesDataset());
        map.put("approval_tasks", approvalTasksDataset());
        return map;
    }

    private DatasetSpec purchaseItemsDataset() {
        String sql = """
            SELECT
              pr.id AS requestId,
              COALESCE(src_pr.req_no, pr.req_no) AS reqNo,
              COALESCE(src_pr.req_title, pr.req_title) AS reqTitle,
              COALESCE(pri.req_type, src_pr.req_type, pr.req_type) AS reqType,
              COALESCE(src_pr.applicant_user_id, pr.applicant_user_id) AS applicantUserId,
              COALESCE(src_pr.purchaser_user_id, pr.purchaser_user_id) AS purchaserUserId,
              COALESCE(src_pr.region_id, pr.region_id) AS regionId,
              COALESCE(src_pr.dept_code, pr.dept_code) AS deptCode,
              COALESCE(src_pr.dept_name, pr.dept_name) AS deptName,
              COALESCE(route_map.businessRegion, '未归属') AS businessRegion,
              COALESCE(route_map.roomGroup, '') AS roomGroup,
              COALESCE(route_map.buildingName, '') AS buildingName,
              CASE
                WHEN COALESCE(src_pr.dept_name, pr.dept_name) IS NULL OR COALESCE(src_pr.dept_name, pr.dept_name) = '' THEN ''
                WHEN LOCATE('-', COALESCE(src_pr.dept_name, pr.dept_name)) > 0 THEN SUBSTRING_INDEX(COALESCE(src_pr.dept_name, pr.dept_name), '-', -1)
                ELSE COALESCE(src_pr.dept_name, pr.dept_name)
              END AS roomName,
              pr.status AS requestStatus,
              pr.approval_status AS approvalStatus,
              COALESCE(applicant.real_name, applicant.username) AS applicantName,
              pri.item_code AS itemCode,
              pri.item_name AS itemName,
              pri.spec_model AS specModel,
              pri.unit AS unit,
              pri.qty AS qty,
              pri.line_amount AS requestAmount,
              COALESCE(pri.receiving_city, aggregate_award.receivingCity, direct_sqi.receiving_city, '') AS receivingCity,
              COALESCE(aggregate_award.supplierId, direct_s.id) AS supplierId,
              COALESCE(aggregate_award.supplierName, direct_s.supplier_name, '未中标') AS supplierName,
              COALESCE(aggregate_award.awardAmount, direct_sqi.line_amount, 0) AS awardAmount,
              COALESCE(aggregate_award.leadTimeDays, direct_sqi.lead_time_days, 0) AS leadTimeDays,
              COALESCE(aggregate_award.awarded, direct_sqi.awarded, 0) AS awarded,
              COALESCE(aggregate_award.supplierConfirmStatus, direct_sq.supplier_confirm_status, 'UNSELECTED') AS supplierConfirmStatus,
              DATE_FORMAT(pr.created_at, '%Y-%m') AS createdMonth,
              pr.created_at AS createdAt,
              pr.submitted_at AS submittedAt,
              pr.approved_at AS approvedAt
            FROM purchase_request_item pri
            JOIN purchase_request pr ON pr.id = pri.purchase_request_id AND pr.is_deleted = 0
            LEFT JOIN purchase_request src_pr ON src_pr.id = pri.source_request_id AND src_pr.is_deleted = 0
            LEFT JOIN sys_user applicant ON applicant.id = COALESCE(src_pr.applicant_user_id, pr.applicant_user_id) AND applicant.is_deleted = 0
            LEFT JOIN (
              SELECT
                dept_code,
                MAX(region_name) AS businessRegion,
                MAX(room_name) AS roomGroup,
                MAX(building_name) AS buildingName
              FROM procurement_approval_route
              WHERE enabled = 1
                AND is_deleted = 0
                AND dept_code IS NOT NULL
                AND dept_code <> ''
              GROUP BY dept_code
            ) route_map ON route_map.dept_code = COALESCE(src_pr.dept_code, pr.dept_code)
            LEFT JOIN supplier_quote_item direct_sqi ON direct_sqi.purchase_request_item_id = pri.id AND direct_sqi.awarded = 1 AND direct_sqi.is_deleted = 0
            LEFT JOIN supplier_quote direct_sq ON direct_sq.id = direct_sqi.quote_id AND direct_sq.is_deleted = 0
            LEFT JOIN supplier direct_s ON direct_s.id = direct_sq.supplier_id AND direct_s.is_deleted = 0
            LEFT JOIN (
              SELECT
                agg_item.source_request_id AS sourceRequestId,
                agg_item.source_request_item_id AS sourceRequestItemId,
                awarded_item.line_amount AS awardAmount,
                awarded_item.lead_time_days AS leadTimeDays,
                awarded_item.awarded AS awarded,
                awarded_item.receiving_city AS receivingCity,
                awarded_quote.supplier_confirm_status AS supplierConfirmStatus,
                supplier.id AS supplierId,
                supplier.supplier_name AS supplierName
              FROM purchase_request_item agg_item
              JOIN purchase_request agg_pr ON agg_pr.id = agg_item.purchase_request_id AND agg_pr.is_deleted = 0
              JOIN supplier_quote_item awarded_item
                ON awarded_item.purchase_request_item_id = agg_item.id
               AND awarded_item.awarded = 1
               AND awarded_item.is_deleted = 0
              JOIN supplier_quote awarded_quote ON awarded_quote.id = awarded_item.quote_id AND awarded_quote.is_deleted = 0
              LEFT JOIN supplier supplier ON supplier.id = awarded_quote.supplier_id AND supplier.is_deleted = 0
              WHERE agg_item.is_deleted = 0
                AND agg_item.source_request_id IS NOT NULL
                AND agg_item.source_request_item_id IS NOT NULL
                AND (agg_item.status IS NULL OR agg_item.status <> 'REJECTED')
                AND (agg_pr.req_reason = '需求池全国月度汇总生成' OR agg_pr.req_no LIKE 'HZCG-%')
            ) aggregate_award
              ON aggregate_award.sourceRequestId = pr.id
             AND aggregate_award.sourceRequestItemId = pri.id
            WHERE pri.is_deleted = 0
              AND (pri.status IS NULL OR pri.status <> 'REJECTED')
              AND NOT (pr.req_reason = '需求池全国月度汇总生成' OR pr.req_no LIKE 'HZCG-%')
            """;
        return dataset("purchase_items", "采购明细表", "申请明细、机房、收货城市、中标供应商和金额交期", sql,
            List.of("reqNo", "itemName", "specModel", "roomName", "supplierName", "receivingCity", "qty", "requestAmount", "awardAmount", "requestStatus"),
            List.of(
                dim("reqNo", "申请单号", "reqNo", "STRING", "BUSINESS_KEY"),
                dim("reqTitle", "申请标题", "reqTitle", "STRING", "TITLE"),
                dim("reqType", "采购类型", "reqType", "STRING", "ENUM"),
                dim("businessRegion", "业务区域", "businessRegion", "STRING", "REGION"),
                dim("roomGroup", "机房群", "roomGroup", "STRING", "ROOM_GROUP"),
                dim("buildingName", "楼栋/机房", "buildingName", "STRING", "ROOM"),
                dim("roomName", "机房简称", "roomName", "STRING", "ROOM"),
                dim("deptCode", "部门编码", "deptCode", "STRING", "ORG"),
                dim("deptName", "完整部门路径", "deptName", "STRING", "ORG"),
                dim("applicantName", "申请人", "applicantName", "STRING", "USER"),
                dim("itemCode", "物料编码", "itemCode", "STRING", "PRODUCT"),
                dim("itemName", "商品名称", "itemName", "STRING", "PRODUCT"),
                dim("specModel", "规格型号", "specModel", "STRING", "PRODUCT_SPEC"),
                dim("unit", "单位", "unit", "STRING", "UNIT"),
                dim("receivingCity", "收货城市", "receivingCity", "STRING", "CITY"),
                dim("supplierName", "中标供应商", "supplierName", "STRING", "PARTNER"),
                measure("requestId", "采购申请数", "requestId", "NUMBER", "COUNT", "单", "COUNT_DISTINCT"),
                measure("supplierId", "成交供应商", "supplierId", "NUMBER", "PARTNER", "家", "COUNT_DISTINCT"),
                measure("qty", "采购数量", "qty", "NUMBER", "QUANTITY", null, "SUM"),
                measure("requestAmount", "需求金额", "requestAmount", "NUMBER", "MONEY", "元", "SUM"),
                measure("awardAmount", "中标金额", "awardAmount", "NUMBER", "MONEY", "元", "SUM"),
                measure("leadTimeDays", "交期", "leadTimeDays", "NUMBER", "DAYS", "天", "AVG"),
                dim("requestStatus", "采购状态", "requestStatus", "STRING", "ENUM"),
                dim("approvalStatus", "审批状态", "approvalStatus", "STRING", "ENUM"),
                dim("supplierConfirmStatus", "供应商确认状态", "supplierConfirmStatus", "STRING", "ENUM"),
                dim("createdMonth", "创建月份", "createdMonth", "STRING", "MONTH"),
                time("createdAt", "创建时间", "createdAt"),
                time("submittedAt", "提交时间", "submittedAt"),
                time("approvedAt", "通过时间", "approvedAt")
            ));
    }

    private DatasetSpec supplierQuoteItemsDataset() {
        String sql = """
            SELECT
              pr.id AS requestId,
              COALESCE(src_pr.applicant_user_id, pr.applicant_user_id) AS applicantUserId,
              COALESCE(src_pr.purchaser_user_id, pr.purchaser_user_id) AS purchaserUserId,
              COALESCE(src_pr.region_id, pr.region_id) AS regionId,
              COALESCE(src_pr.dept_code, pr.dept_code) AS deptCode,
              COALESCE(src_pr.req_no, pr.req_no) AS reqNo,
              COALESCE(src_pr.req_title, pr.req_title) AS reqTitle,
              COALESCE(pri.req_type, src_pr.req_type, pr.req_type) AS reqType,
              COALESCE(src_pr.dept_name, pr.dept_name) AS deptName,
              CASE
                WHEN COALESCE(src_pr.dept_name, pr.dept_name) IS NULL OR COALESCE(src_pr.dept_name, pr.dept_name) = '' THEN ''
                WHEN LOCATE('-', COALESCE(src_pr.dept_name, pr.dept_name)) > 0 THEN SUBSTRING_INDEX(COALESCE(src_pr.dept_name, pr.dept_name), '-', -1)
                ELSE COALESCE(src_pr.dept_name, pr.dept_name)
              END AS roomName,
              COALESCE(applicant.real_name, applicant.username) AS applicantName,
              sq.id AS quoteId,
              sq.quote_no AS quoteNo,
              sq.status AS quoteStatus,
              sq.supplier_confirm_status AS supplierConfirmStatus,
              sq.submitted_at AS submittedAt,
              s.id AS supplierId,
              s.supplier_name AS supplierName,
              sqi.id AS quoteItemId,
              sqi.line_no AS quoteLineNo,
              sqi.item_code AS itemCode,
              sqi.item_name AS itemName,
              sqi.spec_model AS specModel,
              sqi.unit AS unit,
              sqi.qty AS qty,
              sqi.unit_price AS unitPrice,
              sqi.line_amount AS quoteLineAmount,
              CASE WHEN sqi.awarded = 1 THEN sqi.line_amount ELSE 0 END AS awardedAmount,
              sqi.lead_time_days AS leadTimeDays,
              COALESCE(sqi.receiving_city, pri.receiving_city, '') AS receivingCity,
              COALESCE(sqi.delivery_address, pri.delivery_address, '') AS deliveryAddress,
              sqi.replacement_flag AS replacementFlag,
              sqi.awarded AS awarded,
              CASE WHEN sqi.awarded = 1 THEN '已中标' ELSE '未中标' END AS awardStatus,
              sqi.receipt_status AS receiptStatus,
              sqi.received_at AS receivedAt,
              sqi.status AS quoteItemStatus,
              pri.line_amount AS requestAmount,
              pr.created_at AS createdAt
            FROM supplier_quote_item sqi
            JOIN supplier_quote sq ON sq.id = sqi.quote_id AND sq.is_deleted = 0
            JOIN purchase_request pr ON pr.id = sq.purchase_request_id AND pr.is_deleted = 0
            JOIN supplier s ON s.id = sq.supplier_id AND s.is_deleted = 0
            LEFT JOIN purchase_request_item pri ON pri.id = sqi.purchase_request_item_id AND pri.is_deleted = 0
            LEFT JOIN purchase_request src_pr ON src_pr.id = pri.source_request_id AND src_pr.is_deleted = 0
            LEFT JOIN sys_user applicant ON applicant.id = COALESCE(src_pr.applicant_user_id, pr.applicant_user_id) AND applicant.is_deleted = 0
            WHERE sqi.is_deleted = 0
            """;
        return dataset("supplier_quote_items", "供应商报价明细表", "供应商逐项报价、替换商品、中标结果、收货状态和确认状态", sql,
            List.of("quoteNo", "reqNo", "supplierName", "itemName", "specModel", "roomName", "qty", "unitPrice", "quoteLineAmount", "awardStatus", "receiptStatus", "submittedAt"),
            List.of(
                dim("quoteNo", "报价单号", "quoteNo", "STRING", "BUSINESS_KEY"),
                dim("reqNo", "申请单号", "reqNo", "STRING", "BUSINESS_KEY"),
                dim("reqTitle", "申请标题", "reqTitle", "STRING", "TITLE"),
                dim("reqType", "采购类型", "reqType", "STRING", "ENUM"),
                dim("roomName", "机房简称", "roomName", "STRING", "ROOM"),
                dim("deptCode", "部门编码", "deptCode", "STRING", "ORG"),
                dim("deptName", "完整部门路径", "deptName", "STRING", "ORG"),
                dim("applicantName", "申请人", "applicantName", "STRING", "USER"),
                dim("supplierName", "供应商", "supplierName", "STRING", "PARTNER"),
                dim("itemCode", "物料编码", "itemCode", "STRING", "PRODUCT"),
                dim("itemName", "商品名称", "itemName", "STRING", "PRODUCT"),
                dim("specModel", "规格型号", "specModel", "STRING", "PRODUCT_SPEC"),
                dim("unit", "单位", "unit", "STRING", "UNIT"),
                dim("receivingCity", "收货城市", "receivingCity", "STRING", "CITY"),
                dim("deliveryAddress", "送达地址", "deliveryAddress", "STRING", "ADDRESS"),
                dim("awardStatus", "中标状态", "awardStatus", "STRING", "ENUM"),
                dim("quoteStatus", "报价状态", "quoteStatus", "STRING", "ENUM"),
                dim("quoteItemStatus", "报价明细状态", "quoteItemStatus", "STRING", "ENUM"),
                dim("supplierConfirmStatus", "供应商确认状态", "supplierConfirmStatus", "STRING", "ENUM"),
                dim("receiptStatus", "收货状态", "receiptStatus", "STRING", "ENUM"),
                measure("quoteItemId", "报价明细数", "quoteItemId", "NUMBER", "COUNT", "项", "COUNT_DISTINCT"),
                measure("supplierId", "供应商数", "supplierId", "NUMBER", "PARTNER", "家", "COUNT_DISTINCT"),
                measure("qty", "报价数量", "qty", "NUMBER", "QUANTITY", null, "SUM"),
                measure("unitPrice", "报价单价", "unitPrice", "NUMBER", "MONEY", "元", "AVG"),
                measure("quoteLineAmount", "报价金额", "quoteLineAmount", "NUMBER", "MONEY", "元", "SUM"),
                measure("requestAmount", "需求金额", "requestAmount", "NUMBER", "MONEY", "元", "SUM"),
                measure("awarded", "中标明细数", "awarded", "NUMBER", "COUNT", "项", "SUM"),
                measure("awardedAmount", "中标金额", "awardedAmount", "NUMBER", "MONEY", "元", "SUM"),
                measure("leadTimeDays", "交期", "leadTimeDays", "NUMBER", "DAYS", "天", "AVG"),
                measure("replacementFlag", "替换商品数", "replacementFlag", "NUMBER", "COUNT", "项", "SUM"),
                time("submittedAt", "报价提交时间", "submittedAt"),
                time("receivedAt", "收货时间", "receivedAt"),
                time("createdAt", "申请创建时间", "createdAt")
            ));
    }

    private DatasetSpec receiptItemsDataset() {
        String sql = """
            SELECT
              pr.id AS requestId,
              COALESCE(src_pr.applicant_user_id, pr.applicant_user_id) AS applicantUserId,
              COALESCE(src_pr.purchaser_user_id, pr.purchaser_user_id) AS purchaserUserId,
              COALESCE(src_pr.region_id, pr.region_id) AS regionId,
              COALESCE(src_pr.dept_code, pr.dept_code) AS deptCode,
              COALESCE(src_pr.req_no, pr.req_no) AS reqNo,
              COALESCE(src_pr.req_title, pr.req_title) AS reqTitle,
              COALESCE(pri.req_type, src_pr.req_type, pr.req_type) AS reqType,
              COALESCE(src_pr.dept_name, pr.dept_name) AS deptName,
              CASE
                WHEN COALESCE(src_pr.dept_name, pr.dept_name) IS NULL OR COALESCE(src_pr.dept_name, pr.dept_name) = '' THEN ''
                WHEN LOCATE('-', COALESCE(src_pr.dept_name, pr.dept_name)) > 0 THEN SUBSTRING_INDEX(COALESCE(src_pr.dept_name, pr.dept_name), '-', -1)
                ELSE COALESCE(src_pr.dept_name, pr.dept_name)
              END AS roomName,
              COALESCE(applicant.real_name, applicant.username) AS applicantName,
              s.id AS supplierId,
              s.supplier_name AS supplierName,
              sq.id AS quoteId,
              sq.quote_no AS quoteNo,
              sq.supplier_confirm_status AS supplierConfirmStatus,
              sq.supplier_confirmed_at AS supplierConfirmedAt,
              sqi.id AS quoteItemId,
              sqi.line_no AS quoteLineNo,
              sqi.item_code AS itemCode,
              sqi.item_name AS itemName,
              sqi.spec_model AS specModel,
              sqi.unit AS unit,
              sqi.qty AS qty,
              sqi.unit_price AS unitPrice,
              sqi.line_amount AS awardAmount,
              sqi.lead_time_days AS leadTimeDays,
              COALESCE(sqi.receiving_city, pri.receiving_city, '') AS receivingCity,
              COALESCE(sqi.delivery_address, pri.delivery_address, '') AS deliveryAddress,
              COALESCE(sqi.receipt_status, 'PENDING') AS receiptStatus,
              CASE WHEN COALESCE(sqi.receipt_status, 'PENDING') = 'PENDING' THEN 1 ELSE 0 END AS pendingReceipt,
              CASE WHEN sqi.receipt_status = 'RECEIVED' THEN 1 ELSE 0 END AS receivedLine,
              sqi.received_at AS receivedAt,
              sqi.receipt_remark AS receiptRemark,
              pr.created_at AS createdAt
            FROM supplier_quote_item sqi
            JOIN supplier_quote sq ON sq.id = sqi.quote_id AND sq.is_deleted = 0
            JOIN purchase_request pr ON pr.id = sq.purchase_request_id AND pr.is_deleted = 0
            JOIN supplier s ON s.id = sq.supplier_id AND s.is_deleted = 0
            LEFT JOIN purchase_request_item pri ON pri.id = sqi.purchase_request_item_id AND pri.is_deleted = 0
            LEFT JOIN purchase_request src_pr ON src_pr.id = pri.source_request_id AND src_pr.is_deleted = 0
            LEFT JOIN sys_user applicant ON applicant.id = COALESCE(src_pr.applicant_user_id, pr.applicant_user_id) AND applicant.is_deleted = 0
            WHERE sqi.is_deleted = 0
              AND sqi.awarded = 1
            """;
        return dataset("receipt_items", "中标收货明细表", "已中标商品的供应商确认、人工收货状态和交付周期", sql,
            List.of("reqNo", "quoteNo", "supplierName", "itemName", "specModel", "roomName", "receivingCity", "qty", "awardAmount", "leadTimeDays", "supplierConfirmStatus", "receiptStatus", "receivedAt"),
            List.of(
                dim("quoteNo", "报价单号", "quoteNo", "STRING", "BUSINESS_KEY"),
                dim("reqNo", "申请单号", "reqNo", "STRING", "BUSINESS_KEY"),
                dim("reqTitle", "申请标题", "reqTitle", "STRING", "TITLE"),
                dim("reqType", "采购类型", "reqType", "STRING", "ENUM"),
                dim("roomName", "机房简称", "roomName", "STRING", "ROOM"),
                dim("deptCode", "部门编码", "deptCode", "STRING", "ORG"),
                dim("deptName", "完整部门路径", "deptName", "STRING", "ORG"),
                dim("applicantName", "申请人", "applicantName", "STRING", "USER"),
                dim("supplierName", "供应商", "supplierName", "STRING", "PARTNER"),
                dim("itemCode", "物料编码", "itemCode", "STRING", "PRODUCT"),
                dim("itemName", "商品名称", "itemName", "STRING", "PRODUCT"),
                dim("specModel", "规格型号", "specModel", "STRING", "PRODUCT_SPEC"),
                dim("unit", "单位", "unit", "STRING", "UNIT"),
                dim("receivingCity", "收货城市", "receivingCity", "STRING", "CITY"),
                dim("deliveryAddress", "送达地址", "deliveryAddress", "STRING", "ADDRESS"),
                dim("supplierConfirmStatus", "供应商确认状态", "supplierConfirmStatus", "STRING", "ENUM"),
                dim("receiptStatus", "收货状态", "receiptStatus", "STRING", "ENUM"),
                measure("quoteItemId", "中标明细数", "quoteItemId", "NUMBER", "COUNT", "项", "COUNT_DISTINCT"),
                measure("supplierId", "成交供应商", "supplierId", "NUMBER", "PARTNER", "家", "COUNT_DISTINCT"),
                measure("qty", "中标数量", "qty", "NUMBER", "QUANTITY", null, "SUM"),
                measure("unitPrice", "中标单价", "unitPrice", "NUMBER", "MONEY", "元", "AVG"),
                measure("awardAmount", "中标金额", "awardAmount", "NUMBER", "MONEY", "元", "SUM"),
                measure("leadTimeDays", "交期", "leadTimeDays", "NUMBER", "DAYS", "天", "AVG"),
                measure("pendingReceipt", "待收货数", "pendingReceipt", "NUMBER", "COUNT", "项", "SUM"),
                measure("receivedLine", "已收货数", "receivedLine", "NUMBER", "COUNT", "项", "SUM"),
                time("supplierConfirmedAt", "供应商确认时间", "supplierConfirmedAt"),
                time("receivedAt", "收货时间", "receivedAt"),
                time("createdAt", "申请创建时间", "createdAt")
            ));
    }

    private DatasetSpec supplierQuotesDataset() {
        String sql = """
            SELECT
              pr.id AS requestId,
              COALESCE(src_pr.applicant_user_id, pr.applicant_user_id) AS applicantUserId,
              COALESCE(src_pr.purchaser_user_id, pr.purchaser_user_id) AS purchaserUserId,
              COALESCE(src_pr.region_id, pr.region_id) AS regionId,
              COALESCE(src_pr.dept_code, pr.dept_code) AS deptCode,
              sq.id AS quoteId,
              sq.quote_no AS quoteNo,
              COALESCE(src_pr.req_no, pr.req_no) AS reqNo,
              COALESCE(src_pr.req_title, pr.req_title) AS reqTitle,
              COALESCE(src_pr.dept_name, pr.dept_name) AS deptName,
              CASE
                WHEN COALESCE(src_pr.dept_name, pr.dept_name) IS NULL OR COALESCE(src_pr.dept_name, pr.dept_name) = '' THEN ''
                WHEN LOCATE('-', COALESCE(src_pr.dept_name, pr.dept_name)) > 0 THEN SUBSTRING_INDEX(COALESCE(src_pr.dept_name, pr.dept_name), '-', -1)
                ELSE COALESCE(src_pr.dept_name, pr.dept_name)
              END AS roomName,
              s.id AS supplierId,
              s.supplier_name AS supplierName,
              COALESCE(SUM(sqi.line_amount), 0) AS quoteAmount,
              sq.lead_time_days AS leadTimeDays,
              sq.status AS quoteStatus,
              sq.supplier_confirm_status AS supplierConfirmStatus,
              CASE WHEN sq.supplier_confirm_status IN ('PENDING', 'CONFIRMED') THEN 1 ELSE 0 END AS awardedQuote,
              COUNT(DISTINCT sqi.id) AS itemCount,
              COALESCE(SUM(CASE WHEN sqi.awarded = 1 THEN 1 ELSE 0 END), 0) AS awardedItemCount,
              COALESCE(SUM(CASE WHEN sqi.awarded = 1 THEN sqi.line_amount ELSE 0 END), 0) AS awardedAmount,
              sq.submitted_at AS submittedAt,
              sq.created_at AS createdAt
            FROM supplier_quote sq
            JOIN purchase_request pr ON pr.id = sq.purchase_request_id AND pr.is_deleted = 0
            JOIN supplier s ON s.id = sq.supplier_id AND s.is_deleted = 0
            JOIN supplier_quote_item sqi ON sqi.quote_id = sq.id AND sqi.is_deleted = 0
            LEFT JOIN purchase_request_item pri ON pri.id = sqi.purchase_request_item_id AND pri.is_deleted = 0
            LEFT JOIN purchase_request src_pr ON src_pr.id = pri.source_request_id AND src_pr.is_deleted = 0
            WHERE sq.is_deleted = 0
            GROUP BY pr.id,
                     COALESCE(src_pr.applicant_user_id, pr.applicant_user_id),
                     COALESCE(src_pr.purchaser_user_id, pr.purchaser_user_id),
                     COALESCE(src_pr.region_id, pr.region_id),
                     COALESCE(src_pr.dept_code, pr.dept_code),
                     COALESCE(src_pr.dept_name, pr.dept_name),
                     sq.id,
                     sq.quote_no,
                     COALESCE(src_pr.req_no, pr.req_no),
                     COALESCE(src_pr.req_title, pr.req_title),
                     s.id,
                     s.supplier_name,
                     sq.lead_time_days,
                     sq.status,
                     sq.supplier_confirm_status,
                     sq.submitted_at,
                     sq.created_at
            """;
        return dataset("supplier_quotes", "供应商报价汇总表", "报价单、供应商、中标确认、报价金额和交期", sql,
            List.of("quoteNo", "reqNo", "supplierName", "quoteAmount", "leadTimeDays", "quoteStatus", "supplierConfirmStatus", "submittedAt"),
            List.of(
                dim("quoteNo", "报价单号", "quoteNo", "STRING", "BUSINESS_KEY"),
                dim("reqNo", "申请单号", "reqNo", "STRING", "BUSINESS_KEY"),
                dim("reqTitle", "申请标题", "reqTitle", "STRING", "TITLE"),
                dim("roomName", "机房简称", "roomName", "STRING", "ROOM"),
                dim("deptCode", "部门编码", "deptCode", "STRING", "ORG"),
                dim("deptName", "完整部门路径", "deptName", "STRING", "ORG"),
                dim("supplierName", "供应商", "supplierName", "STRING", "PARTNER"),
                measure("supplierId", "供应商数", "supplierId", "NUMBER", "PARTNER", "家", "COUNT_DISTINCT"),
                measure("quoteId", "报价单数", "quoteId", "NUMBER", "COUNT", "单", "COUNT_DISTINCT"),
                measure("quoteAmount", "报价金额", "quoteAmount", "NUMBER", "MONEY", "元", "SUM"),
                measure("leadTimeDays", "交期", "leadTimeDays", "NUMBER", "DAYS", "天", "AVG"),
                measure("awardedQuote", "中标报价数", "awardedQuote", "NUMBER", "COUNT", "单", "SUM"),
                measure("itemCount", "报价明细数", "itemCount", "NUMBER", "COUNT", "项", "SUM"),
                measure("awardedItemCount", "中标明细数", "awardedItemCount", "NUMBER", "COUNT", "项", "SUM"),
                measure("awardedAmount", "中标金额", "awardedAmount", "NUMBER", "MONEY", "元", "SUM"),
                dim("quoteStatus", "报价状态", "quoteStatus", "STRING", "ENUM"),
                dim("supplierConfirmStatus", "供应商确认状态", "supplierConfirmStatus", "STRING", "ENUM"),
                time("submittedAt", "报价提交时间", "submittedAt"),
                time("createdAt", "创建时间", "createdAt")
            ));
    }

    private DatasetSpec approvalTasksDataset() {
        String sql = """
            SELECT
              pr.id AS requestId,
              pr.applicant_user_id AS applicantUserId,
              pr.purchaser_user_id AS purchaserUserId,
              pr.region_id AS regionId,
              pr.dept_code AS deptCode,
              NULL AS supplierId,
              at.id AS taskId,
              pr.req_no AS reqNo,
              pr.req_title AS reqTitle,
              pr.dept_name AS deptName,
              CASE
                WHEN pr.dept_name IS NULL OR pr.dept_name = '' THEN ''
                WHEN LOCATE('-', pr.dept_name) > 0 THEN SUBSTRING_INDEX(pr.dept_name, '-', -1)
                ELSE pr.dept_name
              END AS roomName,
              at.level_name AS levelName,
              at.level_code AS levelCode,
              COALESCE(u.real_name, u.username) AS approverName,
              at.status AS taskStatus,
              CASE WHEN at.status = 'PENDING' AND at.due_at IS NOT NULL AND at.due_at < NOW() THEN 1 ELSE 0 END AS overdue,
              TIMESTAMPDIFF(HOUR, at.created_at, COALESCE(at.action_at, NOW())) AS handlingHours,
              at.created_at AS createdAt,
              at.due_at AS dueAt,
              at.action_at AS actionAt
            FROM approval_task at
            JOIN purchase_request pr ON pr.id = at.purchase_request_id AND pr.is_deleted = 0
            LEFT JOIN sys_user u ON u.id = at.candidate_user_id AND u.is_deleted = 0
            WHERE at.is_deleted = 0
            """;
        return dataset("approval_tasks", "审批过程表", "审批节点、审批人、状态、超期和处理时长", sql,
            List.of("reqNo", "roomName", "levelName", "approverName", "taskStatus", "dueAt", "actionAt"),
            List.of(
                dim("reqNo", "申请单号", "reqNo", "STRING", "BUSINESS_KEY"),
                dim("reqTitle", "申请标题", "reqTitle", "STRING", "TITLE"),
                dim("roomName", "机房简称", "roomName", "STRING", "ROOM"),
                dim("deptCode", "部门编码", "deptCode", "STRING", "ORG"),
                dim("deptName", "完整部门路径", "deptName", "STRING", "ORG"),
                dim("levelName", "审批节点", "levelName", "STRING", "WORKFLOW"),
                dim("levelCode", "节点编码", "levelCode", "STRING", "WORKFLOW"),
                dim("approverName", "审批人", "approverName", "STRING", "USER"),
                dim("taskStatus", "审批状态", "taskStatus", "STRING", "ENUM"),
                measure("taskId", "审批任务数", "taskId", "NUMBER", "COUNT", "个", "COUNT_DISTINCT"),
                measure("overdue", "超期任务数", "overdue", "NUMBER", "COUNT", "个", "SUM"),
                measure("handlingHours", "处理时长", "handlingHours", "NUMBER", "DURATION", "小时", "AVG"),
                time("createdAt", "到达时间", "createdAt"),
                time("dueAt", "截止时间", "dueAt"),
                time("actionAt", "处理时间", "actionAt")
            ));
    }

    private DatasetSpec afterSalesDataset() {
        String sql = """
            SELECT
              pr.id AS requestId,
              COALESCE(src_pr.applicant_user_id, pr.applicant_user_id) AS applicantUserId,
              COALESCE(src_pr.purchaser_user_id, pr.purchaser_user_id) AS purchaserUserId,
              COALESCE(src_pr.region_id, pr.region_id) AS regionId,
              COALESCE(src_pr.dept_code, pr.dept_code) AS deptCode,
              s.id AS supplierId,
              pas.id AS afterSaleId,
              pas.after_sale_no AS afterSaleNo,
              pas.quote_item_id AS quoteItemId,
              COALESCE(src_pr.req_no, pr.req_no) AS reqNo,
              COALESCE(src_pr.req_title, pr.req_title) AS reqTitle,
              COALESCE(pri.req_type, src_pr.req_type, pr.req_type) AS reqType,
              COALESCE(src_pr.dept_name, pr.dept_name) AS deptName,
              CASE
                WHEN COALESCE(src_pr.dept_name, pr.dept_name) IS NULL OR COALESCE(src_pr.dept_name, pr.dept_name) = '' THEN ''
                WHEN LOCATE('-', COALESCE(src_pr.dept_name, pr.dept_name)) > 0 THEN SUBSTRING_INDEX(COALESCE(src_pr.dept_name, pr.dept_name), '-', -1)
                ELSE COALESCE(src_pr.dept_name, pr.dept_name)
              END AS roomName,
              s.supplier_name AS supplierName,
              sq.quote_no AS quoteNo,
              COALESCE(pri.item_code, sqi.item_code) AS itemCode,
              COALESCE(pri.item_name, sqi.item_name) AS itemName,
              COALESCE(pri.spec_model, sqi.spec_model) AS specModel,
              COALESCE(pri.unit, sqi.unit) AS unit,
              sqi.line_amount AS awardAmount,
              sqi.receipt_status AS receiptStatus,
              sqi.received_at AS receivedAt,
              COALESCE(requester.real_name, requester.username) AS requesterName,
              COALESCE(approver.real_name, approver.username) AS approverName,
              pas.after_sale_type AS afterSaleType,
              pas.reason AS reason,
              pas.status AS afterSaleStatus,
              pas.requested_qty AS requestedQty,
              pas.replacement_item_name AS replacementItemName,
              pas.replacement_spec_model AS replacementSpecModel,
              pas.replacement_unit AS replacementUnit,
              pas.replacement_remark AS replacementRemark,
              pas.approval_remark AS approvalRemark,
              pas.created_at AS createdAt,
              pas.approved_at AS approvedAt
            FROM procurement_after_sale pas
            JOIN purchase_request pr ON pr.id = pas.purchase_request_id AND pr.is_deleted = 0
            JOIN supplier s ON s.id = pas.supplier_id AND s.is_deleted = 0
            LEFT JOIN supplier_quote sq ON sq.id = pas.quote_id AND sq.is_deleted = 0
            LEFT JOIN supplier_quote_item sqi ON sqi.id = pas.quote_item_id AND sqi.is_deleted = 0
            LEFT JOIN purchase_request_item pri ON pri.id = COALESCE(pas.purchase_request_item_id, sqi.purchase_request_item_id) AND pri.is_deleted = 0
            LEFT JOIN purchase_request src_pr ON src_pr.id = pri.source_request_id AND src_pr.is_deleted = 0
            LEFT JOIN sys_user requester ON requester.id = pas.requester_user_id AND requester.is_deleted = 0
            LEFT JOIN sys_user approver ON approver.id = pas.approver_user_id AND approver.is_deleted = 0
            WHERE pas.is_deleted = 0
            """;
        return dataset("after_sales", "售后明细表", "售后申请、原商品、替换商品、供应商、收货状态和审核结果", sql,
            List.of("afterSaleNo", "reqNo", "quoteNo", "supplierName", "itemName", "specModel", "roomName", "afterSaleType", "afterSaleStatus", "requestedQty", "replacementItemName", "requesterName", "approverName", "createdAt"),
            List.of(
                dim("afterSaleNo", "售后单号", "afterSaleNo", "STRING", "BUSINESS_KEY"),
                dim("reqNo", "申请单号", "reqNo", "STRING", "BUSINESS_KEY"),
                dim("quoteNo", "报价单号", "quoteNo", "STRING", "BUSINESS_KEY"),
                dim("reqTitle", "申请标题", "reqTitle", "STRING", "TITLE"),
                dim("reqType", "采购类型", "reqType", "STRING", "ENUM"),
                dim("roomName", "机房简称", "roomName", "STRING", "ROOM"),
                dim("deptCode", "部门编码", "deptCode", "STRING", "ORG"),
                dim("deptName", "完整部门路径", "deptName", "STRING", "ORG"),
                dim("supplierName", "供应商", "supplierName", "STRING", "PARTNER"),
                dim("itemCode", "物料编码", "itemCode", "STRING", "PRODUCT"),
                dim("itemName", "商品名称", "itemName", "STRING", "PRODUCT"),
                dim("specModel", "规格型号", "specModel", "STRING", "PRODUCT_SPEC"),
                dim("unit", "单位", "unit", "STRING", "UNIT"),
                dim("afterSaleType", "售后类型", "afterSaleType", "STRING", "ENUM"),
                dim("afterSaleStatus", "售后状态", "afterSaleStatus", "STRING", "ENUM"),
                dim("receiptStatus", "收货状态", "receiptStatus", "STRING", "ENUM"),
                dim("reason", "售后原因", "reason", "STRING", "TEXT"),
                dim("replacementItemName", "替换商品", "replacementItemName", "STRING", "PRODUCT"),
                dim("replacementSpecModel", "替换规格", "replacementSpecModel", "STRING", "PRODUCT_SPEC"),
                dim("replacementUnit", "替换单位", "replacementUnit", "STRING", "UNIT"),
                dim("replacementRemark", "替换说明", "replacementRemark", "STRING", "TEXT"),
                dim("requesterName", "售后申请人", "requesterName", "STRING", "USER"),
                dim("approverName", "售后审核人", "approverName", "STRING", "USER"),
                dim("approvalRemark", "审核意见", "approvalRemark", "STRING", "TEXT"),
                measure("afterSaleId", "售后单数", "afterSaleId", "NUMBER", "COUNT", "单", "COUNT_DISTINCT"),
                measure("quoteItemId", "售后明细数", "quoteItemId", "NUMBER", "COUNT", "项", "COUNT_DISTINCT"),
                measure("requestedQty", "售后数量", "requestedQty", "NUMBER", "QUANTITY", null, "SUM"),
                measure("awardAmount", "中标金额", "awardAmount", "NUMBER", "MONEY", "元", "SUM"),
                time("receivedAt", "收货时间", "receivedAt"),
                time("createdAt", "创建时间", "createdAt"),
                time("approvedAt", "审核时间", "approvedAt")
            ));
    }

    private DatasetSpec dataset(String code, String name, String description, String baseSql, List<String> defaultDetailFields, List<FieldSpec> fields) {
        Map<String, FieldSpec> fieldMap = new LinkedHashMap<>();
        for (FieldSpec field : fields) {
            fieldMap.put(field.code(), field);
        }
        return new DatasetSpec(code, name, description, baseSql, defaultDetailFields, fieldMap);
    }

    private FieldSpec dim(String code, String name, String column, String dataType, String semanticType) {
        return new FieldSpec(code, name, column, dataType, semanticType, "DIMENSION", null, null, true, true, true);
    }

    private FieldSpec time(String code, String name, String column) {
        return new FieldSpec(code, name, column, "DATETIME", "TIME", "DIMENSION", null, null, true, true, true);
    }

    private FieldSpec measure(String code, String name, String column, String dataType, String semanticType, String unit, String aggregation) {
        return new FieldSpec(code, name, column, dataType, semanticType, "MEASURE", unit, aggregation, true, false, true);
    }

    private record DatasetSpec(String code,
                               String name,
                               String description,
                               String baseSql,
                               List<String> defaultDetailFields,
                               Map<String, FieldSpec> fields) {
    }

    private record FieldSpec(String code,
                             String name,
                             String column,
                             String dataType,
                             String semanticType,
                             String role,
                             String unit,
                             String defaultAggregation,
                             boolean filterable,
                             boolean groupable,
                             boolean sortable) {
    }

    private record MetricSelection(FieldSpec field, String aggregation, String outputCode, String sql) {
    }
}
