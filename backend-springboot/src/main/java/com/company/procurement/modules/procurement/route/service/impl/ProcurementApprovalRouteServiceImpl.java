package com.company.procurement.modules.procurement.route.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.procurement.common.export.ExcelExportUtils;
import com.company.procurement.common.export.ExcelImportUtils;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
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
import com.company.procurement.modules.procurement.route.dto.ApprovalLevelSaveDTO;
import com.company.procurement.modules.procurement.route.dto.ProcurementApprovalRouteQueryDTO;
import com.company.procurement.modules.procurement.route.dto.ProcurementApprovalRouteSaveDTO;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.mapper.ProcurementApprovalRouteMapper;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteService;
import com.company.procurement.modules.procurement.route.vo.ApprovalLevelVO;
import com.company.procurement.modules.procurement.route.vo.ApprovalRouteConfigItemVO;
import com.company.procurement.modules.procurement.route.vo.ProcurementApprovalRouteConfigVO;
import com.company.procurement.modules.procurement.route.vo.ProcurementApprovalRouteVO;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProcurementApprovalRouteServiceImpl implements ProcurementApprovalRouteService {

    private final ProcurementApprovalRouteMapper routeMapper;
    private final ProcurementApprovalRouteResolver approvalRouteResolver;
    private final OrgScopeMapper orgScopeMapper;
    private final ProcurementApprovalPolicyMapper policyMapper;
    private final ProcurementApprovalLevelMapper levelMapper;
    private final ProcurementApprovalLevelUserMapper levelUserMapper;
    private final SysUserMapper userMapper;

    private static final String MANAGE_PERMISSION = "procurement_route:manage";
    private static final String ADDRESS_PERMISSION = "procurement_route:address";
    private static final String IMPORT_PERMISSION = "procurement_route:import";
    private static final List<String> ROUTE_IMPORT_HEADERS = buildRouteImportHeaders();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementApprovalRouteConfigVO getConfig(ProcurementApprovalRouteQueryDTO queryDTO) {
        ensureRouteAccessPermission();
        ensureDefaultRoute();
        ProcurementApprovalRouteQueryDTO query = queryDTO == null ? new ProcurementApprovalRouteQueryDTO() : queryDTO;
        boolean includeApprovalLevels = query.getIncludeApprovalLevels() == null || Boolean.TRUE.equals(query.getIncludeApprovalLevels());
        ProcurementApprovalRouteConfigVO vo = new ProcurementApprovalRouteConfigVO();
        if (query.getIncludeRoutes() == null || Boolean.TRUE.equals(query.getIncludeRoutes())) {
            vo.setRoutes(routeMapper.selectList(buildQuery(query)).stream()
                .map(route -> includeApprovalLevels ? toVO(route) : toSummaryVO(route))
                .toList());
        } else {
            vo.setRoutes(List.of());
        }
        if (query.getIncludeRules() == null || Boolean.TRUE.equals(query.getIncludeRules())) {
            vo.setWorkflowPaths(workflowPaths());
            vo.setRolePermissions(rolePermissions());
            vo.setApprovalRules(approvalRules());
            vo.setSupplierPermissions(supplierPermissions());
            vo.setFieldMappings(fieldMappings());
        } else {
            vo.setWorkflowPaths(List.of());
            vo.setRolePermissions(List.of());
            vo.setApprovalRules(List.of());
            vo.setSupplierPermissions(List.of());
            vo.setFieldMappings(List.of());
        }
        return vo;
    }

    @Override
    public List<ProcurementApprovalRouteVO> listEnabledRoutes() {
        ensureDefaultRoute();
        return routeMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalRoute>()
                .eq(ProcurementApprovalRoute::getEnabled, 1)
                .orderByAsc(ProcurementApprovalRoute::getRegionName)
                .orderByAsc(ProcurementApprovalRoute::getRoomName)
                .orderByAsc(ProcurementApprovalRoute::getBuildingName))
            .stream()
            .map(this::toVO)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRoute(ProcurementApprovalRouteSaveDTO dto) {
        ensureManagePermission();
        ProcurementApprovalRoute route = new ProcurementApprovalRoute();
        applySaveDTO(route, dto);
        if (dto.getApprovalLevels() == null || dto.getApprovalLevels().isEmpty()) {
            approvalRouteResolver.validateRouteCandidates(route);
        }
        ensureUniqueRouteKey(route);
        routeMapper.insert(route);
        syncApprovalPolicy(route, dto);
        return route.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoute(Long id, ProcurementApprovalRouteSaveDTO dto) {
        ensureManagePermission();
        ProcurementApprovalRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new BizException("APPROVAL_ROUTE_NOT_FOUND", "审批路由不存在");
        }
        applySaveDTO(route, dto);
        if (dto.getApprovalLevels() == null || dto.getApprovalLevels().isEmpty()) {
            approvalRouteResolver.validateRouteCandidates(route);
        }
        ensureUniqueRouteKey(route);
        routeMapper.updateById(route);
        syncApprovalPolicy(route, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRouteAddress(Long id, ProcurementApprovalRouteSaveDTO dto) {
        ensureAddressPermission();
        ProcurementApprovalRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new BizException("APPROVAL_ROUTE_NOT_FOUND", "Approval route not found");
        }
        ensureScope(route, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoute(Long id) {
        ensureManagePermission();
        ProcurementApprovalRoute route = routeMapper.selectById(id);
        if (route == null) {
            return;
        }
        route.setRouteKey(route.getRouteKey() + "#deleted#" + id);
        routeMapper.updateById(route);
        routeMapper.deleteById(id);
    }

    @Override
    public ExportFile exportImportTemplate(ProcurementApprovalRouteQueryDTO queryDTO) {
        ensureImportPermission();
        ensureDefaultRoute();
        List<List<String>> rows = new ArrayList<>();
        rows.add(ROUTE_IMPORT_HEADERS);
        routeMapper.selectList(buildQuery(queryDTO)).stream()
            .map(this::toVO)
            .forEach(route -> rows.add(buildRouteImportRow(route)));
        return new ExportFile(
            "部门审批配置批量维护模板.xlsx",
            ExcelExportUtils.XLSX_CONTENT_TYPE,
            ExcelExportUtils.toXlsxBytes("部门审批配置", rows, routeImportTemplateOptions())
        );
    }

    private ExcelExportUtils.WorkbookOptions routeImportTemplateOptions() {
        ExcelExportUtils.Builder builder = ExcelExportUtils.WorkbookOptions.builder()
            .freezeHeader(true)
            .autoFilter(true)
            .requiredColumns(column("部门ID"), column("路由编码"), column("状态"))
            .dropdown(column("状态"), List.of("启用", "停用"))
            .note(0, column("部门ID"), "系统身份列，请不要修改；导入时会校验是否与当前系统数据一致。")
            .note(0, column("路由编码"), "系统身份列，请不要修改；导入时会校验是否与当前系统数据一致。")
            .note(0, column("状态"), "请选择启用或停用。")
            .note(0, column("需求池推送日"), "填写 1-31 的日期数字；为空时保持原配置。")
            .note(0, column("区域经理门槛"), "金额阈值列，只填数字。")
            .note(0, column("总监门槛"), "金额阈值列，只填数字。");
        for (String header : List.of("机房", "楼栋", "区域", "成本中心编码", "部门编号", "部门名称")) {
            builder.note(0, column(header), "系统识别列，原则上不要修改；导入时会校验。");
        }
        for (int levelNo = 1; levelNo <= 8; levelNo++) {
            String prefix = "V" + levelNo;
            builder
                .dropdown(column(prefix + "启用"), List.of("启用", "停用"))
                .dropdown(column(prefix + "触发方式"), List.of("始终触发", "专业采购触发", "金额触发", "手动启用"))
                .dropdown(column(prefix + "通过规则"), List.of("任意一人通过", "全部通过"))
                .dropdown(column(prefix + "无人跳过"), List.of("是", "否"))
                .note(0, column(prefix + "启用"), "请选择启用或停用。")
                .note(0, column(prefix + "触发方式"), "请选择节点触发条件。")
                .note(0, column(prefix + "通过规则"), "请选择任意一人通过或全部通过。")
                .note(0, column(prefix + "候选人工号"), "多个候选人用英文逗号、中文逗号、分号或空格分隔；系统会按工号和姓名校验。")
                .note(0, column(prefix + "候选人姓名"), "用于校验候选人工号是否填错，多个姓名与工号顺序保持一致。")
                .note(0, column(prefix + "金额门槛"), "金额触发节点填写数字；非金额触发可为空。")
                .note(0, column(prefix + "无人跳过"), "候选人为空时是否跳过该节点。");
        }
        return builder.build();
    }

    private int column(String header) {
        return ROUTE_IMPORT_HEADERS.indexOf(header);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importRouteTemplate(MultipartFile file) {
        ensureImportPermission();
        List<List<String>> table = ExcelImportUtils.readFirstSheet(file);
        if (table.size() < 2) {
            throw new BizException("ROUTE_IMPORT_EMPTY", "导入文件没有可维护的部门数据");
        }
        validateRouteImportHeader(table.get(0));
        Map<Long, ProcurementApprovalRoute> routeMap = routeMapper.selectList(new LambdaQueryWrapper<>()).stream()
            .collect(LinkedHashMap::new, (map, route) -> map.put(route.getId(), route), LinkedHashMap::putAll);
        List<RouteImportPlan> plans = new ArrayList<>();
        Map<Long, Integer> seenRouteRows = new LinkedHashMap<>();
        for (int rowIndex = 1; rowIndex < table.size(); rowIndex++) {
            List<String> row = table.get(rowIndex);
            if (row == null || row.stream().noneMatch(StringUtils::hasText)) {
                continue;
            }
            int displayRowNo = rowIndex + 1;
            Long routeId = parseRequiredLong(cell(row, "部门ID"), "第" + displayRowNo + "行部门ID");
            if (seenRouteRows.putIfAbsent(routeId, displayRowNo) != null) {
                throw new BizException("ROUTE_IMPORT_DUPLICATED", "第" + displayRowNo + "行部门ID重复：" + routeId);
            }
            ProcurementApprovalRoute route = routeMap.get(routeId);
            if (route == null) {
                throw new BizException("ROUTE_IMPORT_ROUTE_NOT_FOUND", "第" + displayRowNo + "行部门ID不存在：" + routeId);
            }
            plans.add(buildImportPlan(row, displayRowNo, route));
        }
        if (plans.isEmpty()) {
            throw new BizException("ROUTE_IMPORT_EMPTY", "导入文件没有可维护的部门数据");
        }
        for (RouteImportPlan plan : plans) {
            updateRoute(plan.routeId(), plan.dto());
        }
        return plans.size();
    }

    private static List<String> buildRouteImportHeaders() {
        List<String> headers = new ArrayList<>(List.of(
            "部门ID",
            "路由编码",
            "状态",
            "机房",
            "楼栋",
            "区域",
            "成本中心编码",
            "部门编号",
            "部门名称",
            "省份",
            "城市",
            "区县",
            "乡镇/街道",
            "详细地址",
            "需求池推送日",
            "区域经理门槛",
            "总监门槛",
            "备注"
        ));
        for (int levelNo = 1; levelNo <= 8; levelNo++) {
            String prefix = "V" + levelNo;
            headers.add(prefix + "启用");
            headers.add(prefix + "节点名称");
            headers.add(prefix + "触发方式");
            headers.add(prefix + "通过规则");
            headers.add(prefix + "候选人工号");
            headers.add(prefix + "候选人姓名");
            headers.add(prefix + "金额门槛");
            headers.add(prefix + "无人跳过");
        }
        return List.copyOf(headers);
    }

    private List<String> buildRouteImportRow(ProcurementApprovalRouteVO route) {
        Map<Integer, ApprovalLevelVO> levels = new HashMap<>();
        if (route.getApprovalLevels() != null) {
            route.getApprovalLevels().forEach(level -> levels.put(level.getLevelNo(), level));
        }
        List<String> row = new ArrayList<>(List.of(
            text(route.getId()),
            text(route.getRouteKey()),
            route.getEnabled() != null && route.getEnabled() == 1 ? "启用" : "停用",
            text(route.getRoomName()),
            text(route.getBuildingName()),
            text(route.getRegionName()),
            text(route.getCostCenterCode()),
            text(route.getDeptCode()),
            text(route.getDeptName()),
            text(route.getProvince()),
            text(route.getCity()),
            text(route.getDistrictName()),
            text(route.getTownName()),
            text(route.getAddress()),
            text(route.getPoolPushDay()),
            moneyText(route.getRegionThreshold()),
            moneyText(route.getDirectorThreshold()),
            text(route.getRemark())
        ));
        for (int levelNo = 1; levelNo <= 8; levelNo++) {
            ApprovalLevelVO level = levels.get(levelNo);
            if (level == null) {
                row.addAll(List.of("", "", "", "", "", "", "", ""));
                continue;
            }
            row.add(level.getEnabled() != null && level.getEnabled() == 1 ? "启用" : "停用");
            row.add(text(level.getLevelName()));
            row.add(triggerModeLabel(level.getTriggerMode()));
            row.add(passRuleLabel(level.getPassRule()));
            row.add(joinEmployeeNos(parseUserIds(level.getCandidateUserIds())));
            row.add(joinUserRealNames(parseUserIds(level.getCandidateUserIds())));
            row.add(moneyText(level.getAmountMin()));
            row.add(level.getSkipIfEmpty() != null && level.getSkipIfEmpty() == 1 ? "是" : "否");
        }
        return row;
    }

    private void validateRouteImportHeader(List<String> headerRow) {
        if (headerRow == null || headerRow.size() != ROUTE_IMPORT_HEADERS.size()) {
            throw new BizException("ROUTE_IMPORT_HEADER_INVALID", "导入模板列数不一致，请先导出最新模板后再修改");
        }
        for (int i = 0; i < ROUTE_IMPORT_HEADERS.size(); i++) {
            String expected = ExcelImportUtils.normalizeHeader(ROUTE_IMPORT_HEADERS.get(i));
            String actual = ExcelImportUtils.normalizeHeader(headerRow.get(i));
            if (!Objects.equals(expected, actual)) {
                throw new BizException(
                    "ROUTE_IMPORT_HEADER_INVALID",
                    "导入模板第" + (i + 1) + "列表头应为「" + ROUTE_IMPORT_HEADERS.get(i) + "」"
                );
            }
        }
    }

    private RouteImportPlan buildImportPlan(List<String> row, int rowNo, ProcurementApprovalRoute route) {
        assertCellMatches(row, rowNo, "路由编码", route.getRouteKey());
        assertCellMatches(row, rowNo, "机房", route.getRoomName());
        assertCellMatches(row, rowNo, "楼栋", route.getBuildingName());
        assertCellMatches(row, rowNo, "区域", route.getRegionName());
        assertCellMatches(row, rowNo, "成本中心编码", route.getCostCenterCode());
        assertCellMatches(row, rowNo, "部门编号", route.getDeptCode());
        assertCellMatches(row, rowNo, "部门名称", route.getDeptName());

        ProcurementApprovalRouteSaveDTO dto = new ProcurementApprovalRouteSaveDTO();
        dto.setRouteKey(route.getRouteKey());
        dto.setEnabled(parseEnabled(cell(row, "状态"), rowNo, "状态"));
        dto.setRoomNature(route.getRoomNature());
        dto.setRoomName(route.getRoomName());
        dto.setBuildingName(route.getBuildingName());
        dto.setRegionName(route.getRegionName());
        dto.setCostCenterCode(route.getCostCenterCode());
        dto.setDeptCode(route.getDeptCode());
        dto.setDeptName(route.getDeptName());
        dto.setProvince(trimToNull(cell(row, "省份")));
        dto.setCity(trimToNull(cell(row, "城市")));
        dto.setDistrictName(trimToNull(cell(row, "区县")));
        dto.setTownName(trimToNull(cell(row, "乡镇/街道")));
        dto.setAddress(trimToNull(cell(row, "详细地址")));
        dto.setPoolPushDay(parseIntRange(cell(row, "需求池推送日"), rowNo, "需求池推送日", 1, 31));
        dto.setRegionThreshold(parseAmount(cell(row, "区域经理门槛"), rowNo, "区域经理门槛"));
        dto.setDirectorThreshold(parseAmount(cell(row, "总监门槛"), rowNo, "总监门槛"));
        dto.setOverPriceRule(route.getOverPriceRule());
        dto.setReplacementConfirmNames(route.getReplacementConfirmNames());
        dto.setSupplierQuoteScope(route.getSupplierQuoteScope());
        dto.setRemark(trimToNull(cell(row, "备注")));

        List<ApprovalLevelSaveDTO> levels = buildImportLevels(row, rowNo, route);
        dto.setApprovalLevels(levels);
        applyBoundCandidateSummary(dto, levels);
        return new RouteImportPlan(route.getId(), dto);
    }

    private List<ApprovalLevelSaveDTO> buildImportLevels(List<String> row, int rowNo, ProcurementApprovalRoute route) {
        Map<Integer, ApprovalLevelVO> existingLevels = new HashMap<>();
        loadApprovalLevels(route).forEach(level -> existingLevels.put(level.getLevelNo(), level));
        Map<Integer, ApprovalLevelSaveDTO> defaultLevels = new HashMap<>();
        defaultLevelDtos(route).forEach(level -> defaultLevels.put(level.getLevelNo(), level));
        List<ApprovalLevelSaveDTO> levels = new ArrayList<>();
        for (int levelNo = 1; levelNo <= 8; levelNo++) {
            String prefix = "V" + levelNo;
            ApprovalLevelVO existing = existingLevels.get(levelNo);
            ApprovalLevelSaveDTO fallback = defaultLevels.get(levelNo);
            ApprovalLevelSaveDTO dto = new ApprovalLevelSaveDTO();
            dto.setLevelNo(levelNo);
            dto.setLevelCode(existing == null ? fallback.getLevelCode() : existing.getLevelCode());
            dto.setRoleCode(existing == null ? fallback.getRoleCode() : existing.getRoleCode());
            dto.setLevelName(defaultText(cell(row, prefix + "节点名称"), existing == null ? fallback.getLevelName() : existing.getLevelName()));
            dto.setEnabled(parseEnabled(cell(row, prefix + "启用"), rowNo, prefix + "启用"));
            dto.setTriggerMode(normalizeTriggerMode(cell(row, prefix + "触发方式"), rowNo, prefix + "触发方式"));
            dto.setPassRule(normalizePassRule(cell(row, prefix + "通过规则")));
            dto.setAmountMin(parseOptionalAmount(cell(row, prefix + "金额门槛"), rowNo, prefix + "金额门槛"));
            dto.setAmountMax(existing == null ? fallback.getAmountMax() : existing.getAmountMax());
            dto.setSkipIfEmpty(parseYesNo(cell(row, prefix + "无人跳过"), rowNo, prefix + "无人跳过"));
            List<Long> candidateIds = resolveImportCandidateIds(
                rowNo,
                prefix,
                cell(row, prefix + "候选人工号"),
                cell(row, prefix + "候选人姓名")
            );
            dto.setCandidateUserIds(joinIds(candidateIds));
            dto.setCandidateNames(joinUserNames(candidateIds));
            dto.setRemark(existing == null ? fallback.getRemark() : existing.getRemark());
            levels.add(dto);
        }
        return levels;
    }

    private void applyBoundCandidateSummary(ProcurementApprovalRouteSaveDTO dto, List<ApprovalLevelSaveDTO> levels) {
        levelByNo(levels, 3).ifPresent(level -> {
            dto.setFloorLeaderNames(level.getCandidateNames());
            dto.setFloorLeaderRule(passRuleLabel(level.getPassRule()));
        });
        levelByNo(levels, 4).ifPresent(level -> {
            dto.setManagerNames(level.getCandidateNames());
            dto.setManagerRule(passRuleLabel(level.getPassRule()));
        });
        levelByNo(levels, 5).ifPresent(level -> {
            dto.setRegionManagerNames(level.getCandidateNames());
            dto.setRegionManagerRule(passRuleLabel(level.getPassRule()));
        });
        levelByNo(levels, 6).ifPresent(level -> {
            dto.setDirectorNames(level.getCandidateNames());
            dto.setDirectorRule(passRuleLabel(level.getPassRule()));
        });
    }

    private java.util.Optional<ApprovalLevelSaveDTO> levelByNo(List<ApprovalLevelSaveDTO> levels, int levelNo) {
        return levels.stream().filter(level -> Objects.equals(level.getLevelNo(), levelNo)).findFirst();
    }

    private List<Long> resolveImportCandidateIds(int rowNo, String levelCode, String employeeNos, String candidateNames) {
        List<Long> ids = new ArrayList<>();
        List<String> employeeNoList = splitImportTokens(employeeNos);
        if (!employeeNoList.isEmpty()) {
            for (String employeeNo : employeeNoList) {
                SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getStatus, 1)
                    .eq(SysUser::getEmployeeNo, employeeNo)
                    .last("LIMIT 1"));
                if (user == null) {
                    throw new BizException("ROUTE_IMPORT_USER_NOT_FOUND", "第" + rowNo + "行" + levelCode + "候选人工号不存在或未启用：" + employeeNo);
                }
                ids.add(user.getId());
            }
            validateCandidateNames(rowNo, levelCode, ids, candidateNames);
            return ids.stream().distinct().toList();
        }
        for (String name : splitImportTokens(candidateNames)) {
            List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .and(wrapper -> wrapper.eq(SysUser::getRealName, name).or().eq(SysUser::getUsername, name)));
            if (users.isEmpty()) {
                throw new BizException("ROUTE_IMPORT_USER_NOT_FOUND", "第" + rowNo + "行" + levelCode + "候选人不存在或未启用：" + name);
            }
            if (users.size() > 1) {
                throw new BizException("ROUTE_IMPORT_USER_DUPLICATED", "第" + rowNo + "行" + levelCode + "候选人姓名重复，请填写工号：" + name);
            }
            ids.add(users.get(0).getId());
        }
        return ids.stream().distinct().toList();
    }

    private void validateCandidateNames(int rowNo, String levelCode, List<Long> candidateIds, String candidateNames) {
        List<String> names = splitImportTokens(candidateNames);
        if (names.isEmpty()) {
            return;
        }
        List<String> resolvedNames = userMapper.selectBatchIds(candidateIds).stream()
            .sorted(Comparator.comparing(user -> candidateIds.indexOf(user.getId())))
            .map(user -> StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername())
            .toList();
        if (!Objects.equals(names, resolvedNames)) {
            throw new BizException("ROUTE_IMPORT_USER_MISMATCH", "第" + rowNo + "行" + levelCode + "候选人工号与姓名不匹配");
        }
    }

    private List<String> splitImportTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return Arrays.stream(text.split("[,，、/;；\\s]+"))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private void assertCellMatches(List<String> row, int rowNo, String header, String expectedValue) {
        String actual = normalizeCell(cell(row, header));
        String expected = normalizeCell(expectedValue);
        if (!Objects.equals(actual, expected)) {
            throw new BizException("ROUTE_IMPORT_IDENTITY_MISMATCH", "第" + rowNo + "行" + header + "与当前系统数据不一致，请重新导出模板");
        }
    }

    private String cell(List<String> row, String header) {
        int index = ROUTE_IMPORT_HEADERS.indexOf(header);
        if (index < 0 || row == null || index >= row.size()) {
            return "";
        }
        return row.get(index) == null ? "" : row.get(index).trim();
    }

    private String normalizeCell(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private Long parseRequiredLong(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new BizException("ROUTE_IMPORT_REQUIRED", label + "不能为空");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new BizException("ROUTE_IMPORT_NUMBER_INVALID", label + "不是有效数字");
        }
    }

    private int parseEnabled(String value, int rowNo, String label) {
        if (!StringUtils.hasText(value)) {
            throw new BizException("ROUTE_IMPORT_REQUIRED", "第" + rowNo + "行" + label + "不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (List.of("1", "启用", "是", "true", "enabled", "active").contains(normalized)) {
            return 1;
        }
        if (List.of("0", "停用", "否", "false", "disabled", "inactive").contains(normalized)) {
            return 0;
        }
        throw new BizException("ROUTE_IMPORT_STATUS_INVALID", "第" + rowNo + "行" + label + "只能填写启用或停用");
    }

    private int parseYesNo(String value, int rowNo, String label) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (List.of("1", "是", "true", "yes", "y", "跳过").contains(normalized)) {
            return 1;
        }
        if (List.of("0", "否", "false", "no", "n", "不跳过").contains(normalized)) {
            return 0;
        }
        throw new BizException("ROUTE_IMPORT_BOOLEAN_INVALID", "第" + rowNo + "行" + label + "只能填写是或否");
    }

    private int parseIntRange(String value, int rowNo, String label, int min, int max) {
        Long parsed = parseRequiredLong(value, "第" + rowNo + "行" + label);
        if (parsed < min || parsed > max) {
            throw new BizException("ROUTE_IMPORT_NUMBER_INVALID", "第" + rowNo + "行" + label + "必须在 " + min + "-" + max + " 之间");
        }
        return parsed.intValue();
    }

    private BigDecimal parseAmount(String value, int rowNo, String label) {
        BigDecimal amount = parseOptionalAmount(value, rowNo, label);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BigDecimal parseOptionalAmount(String value, int rowNo, String label) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").replace("￥", "").trim());
        } catch (NumberFormatException ex) {
            throw new BizException("ROUTE_IMPORT_AMOUNT_INVALID", "第" + rowNo + "行" + label + "不是有效金额");
        }
    }

    private String normalizeTriggerMode(String value, int rowNo, String label) {
        String mode = StringUtils.hasText(value) ? value.trim() : "ALWAYS";
        return switch (mode) {
            case "始终触发", "ALWAYS" -> "ALWAYS";
            case "专业采购触发", "BY_SPECIALTY" -> "BY_SPECIALTY";
            case "金额触发", "AMOUNT_GT" -> "AMOUNT_GT";
            case "手动启用", "MANUAL" -> "MANUAL";
            default -> throw new BizException("ROUTE_IMPORT_TRIGGER_INVALID", "第" + rowNo + "行" + label + "不是有效触发方式");
        };
    }

    private String triggerModeLabel(String value) {
        return switch (value == null ? "" : value) {
            case "BY_SPECIALTY" -> "专业采购触发";
            case "AMOUNT_GT" -> "金额触发";
            case "MANUAL" -> "手动启用";
            default -> "始终触发";
        };
    }

    private String passRuleLabel(String passRule) {
        return "ALL".equalsIgnoreCase(passRule) ? "全部通过" : "任意一人通过";
    }

    private String joinEmployeeNos(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<SysUser> users = userMapper.selectBatchIds(ids);
        List<String> employeeNos = new ArrayList<>();
        for (Long id : ids) {
            users.stream()
                .filter(user -> Objects.equals(user.getId(), id))
                .findFirst()
                .map(SysUser::getEmployeeNo)
                .filter(StringUtils::hasText)
                .ifPresent(employeeNo -> employeeNos.add(employeeNo.trim()));
        }
        return String.join(",", employeeNos);
    }

    private String joinUserRealNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<SysUser> users = userMapper.selectBatchIds(ids);
        List<String> names = new ArrayList<>();
        for (Long id : ids) {
            users.stream()
                .filter(user -> Objects.equals(user.getId(), id))
                .findFirst()
                .map(user -> StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername())
                .filter(StringUtils::hasText)
                .ifPresent(names::add);
        }
        return String.join(",", names);
    }

    private String moneyText(BigDecimal amount) {
        return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void ensureRouteAccessPermission() {
        if (!hasRoutePermission(MANAGE_PERMISSION)
            && !hasRoutePermission(ADDRESS_PERMISSION)
            && !hasRoutePermission(IMPORT_PERMISSION)) {
            throw new BizException("FORBIDDEN", "No permission to access department approval routes");
        }
    }

    private void ensureManagePermission() {
        if (!hasRoutePermission(MANAGE_PERMISSION)) {
            throw new BizException("FORBIDDEN", "No permission to manage department approval routes");
        }
    }

    private void ensureAddressPermission() {
        if (!hasRoutePermission(MANAGE_PERMISSION) && !hasRoutePermission(ADDRESS_PERMISSION)) {
            throw new BizException("FORBIDDEN", "No permission to maintain department addresses");
        }
    }

    private void ensureImportPermission() {
        if (!hasRoutePermission(IMPORT_PERMISSION)) {
            throw new BizException("FORBIDDEN", "No permission to import department approval routes");
        }
    }

    private boolean hasRoutePermission(String permissionCode) {
        AuthUser authUser = AuthContextHolder.get();
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        if (authUser == null || authUser.getPermissionCodes() == null) {
            return false;
        }
        return authUser.getPermissionCodes().contains("*")
            || authUser.getPermissionCodes().contains("*:*:*")
            || authUser.getPermissionCodes().contains(permissionCode);
    }

    private LambdaQueryWrapper<ProcurementApprovalRoute> buildQuery(ProcurementApprovalRouteQueryDTO queryDTO) {
        ProcurementApprovalRouteQueryDTO query = queryDTO == null ? new ProcurementApprovalRouteQueryDTO() : queryDTO;
        LambdaQueryWrapper<ProcurementApprovalRoute> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w
                .like(ProcurementApprovalRoute::getRoomName, keyword)
                .or().like(ProcurementApprovalRoute::getBuildingName, keyword)
                .or().like(ProcurementApprovalRoute::getDeptName, keyword)
                .or().like(ProcurementApprovalRoute::getFloorLeaderNames, keyword)
                .or().like(ProcurementApprovalRoute::getManagerNames, keyword)
                .or().like(ProcurementApprovalRoute::getRegionManagerNames, keyword)
                .or().like(ProcurementApprovalRoute::getDirectorNames, keyword));
        }
        if (StringUtils.hasText(query.getRoomName())) {
            wrapper.like(ProcurementApprovalRoute::getRoomName, query.getRoomName().trim());
        }
        if (StringUtils.hasText(query.getRegionName())) {
            wrapper.like(ProcurementApprovalRoute::getRegionName, query.getRegionName().trim());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(ProcurementApprovalRoute::getEnabled, query.getEnabled());
        }
        if (query.getRouteId() != null) {
            wrapper.eq(ProcurementApprovalRoute::getId, query.getRouteId());
        }
        wrapper.orderByDesc(ProcurementApprovalRoute::getEnabled)
            .orderByAsc(ProcurementApprovalRoute::getRegionName)
            .orderByAsc(ProcurementApprovalRoute::getRoomName)
            .orderByAsc(ProcurementApprovalRoute::getBuildingName);
        return wrapper;
    }

    private void ensureDefaultRoute() {
        Long count = routeMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        ProcurementApprovalRoute route = new ProcurementApprovalRoute();
        route.setEnabled(1);
        route.setRouteKey("酒仙桥|M6|N420N00077");
        route.setRoomNature("自建");
        route.setRoomName("酒仙桥");
        route.setBuildingName("M6");
        route.setCostCenterCode("N420N00077");
        route.setDeptCode("224");
        route.setDeptName("[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6");
        route.setFloorLeaderNames("赵新,马金礼");
        route.setFloorLeaderRule("任意一人通过");
        route.setManagerNames("张强,刘燚");
        route.setManagerRule("任意一人通过");
        route.setRegionName("华北一");
        route.setRegionManagerNames("王郑建");
        route.setRegionManagerRule("任意一人通过");
        route.setDirectorNames("杨磊");
        route.setDirectorRule("任意一人通过");
        route.setPoolPushDay(20);
        route.setRegionThreshold(new BigDecimal("5000.00"));
        route.setDirectorThreshold(new BigDecimal("100000.00"));
        route.setOverPriceRule("单项实际价 > 参考价 * 113%");
        route.setReplacementConfirmNames("赵新,马金礼");
        route.setSupplierQuoteScope("仅可见推送给自己的商品明细");
        route.setRemark("M6 示例审批配置");
        routeMapper.insert(route);
    }

    private ProcurementApprovalRouteVO toVO(ProcurementApprovalRoute route) {
        ProcurementApprovalRouteVO vo = toBasicVO(route);
        List<ApprovalLevelVO> levels = loadApprovalLevels(route);
        vo.setFloorLeaderNames(boundCandidateNames(levels, 3));
        vo.setManagerNames(boundCandidateNames(levels, 4));
        vo.setRegionManagerNames(boundCandidateNames(levels, 5));
        vo.setDirectorNames(boundCandidateNames(levels, 6));
        applyApprovalSummary(vo, levels);
        vo.setApprovalLevels(levels);
        return vo;
    }

    private ProcurementApprovalRouteVO toSummaryVO(ProcurementApprovalRoute route) {
        ProcurementApprovalRouteVO vo = toBasicVO(route);
        applyApprovalSummary(vo, loadApprovalLevels(route));
        return vo;
    }

    private ProcurementApprovalRouteVO toBasicVO(ProcurementApprovalRoute route) {
        ProcurementApprovalRouteVO vo = new ProcurementApprovalRouteVO();
        vo.setId(route.getId());
        vo.setEnabled(route.getEnabled());
        vo.setRouteKey(route.getRouteKey());
        vo.setRoomNature(route.getRoomNature());
        vo.setRoomName(route.getRoomName());
        vo.setBuildingName(route.getBuildingName());
        vo.setCostCenterCode(route.getCostCenterCode());
        vo.setDeptCode(route.getDeptCode());
        populateAddress(vo, findScope(route));
        vo.setDeptName(route.getDeptName());
        vo.setFloorLeaderNames(route.getFloorLeaderNames());
        vo.setFloorLeaderRule(route.getFloorLeaderRule());
        vo.setManagerNames(route.getManagerNames());
        vo.setManagerRule(route.getManagerRule());
        vo.setAssistantUserIds(route.getAssistantUserIds());
        vo.setAssistantNames(route.getAssistantNames());
        vo.setRegionName(route.getRegionName());
        vo.setRegionManagerNames(route.getRegionManagerNames());
        vo.setRegionManagerRule(route.getRegionManagerRule());
        vo.setDirectorNames(route.getDirectorNames());
        vo.setDirectorRule(route.getDirectorRule());
        vo.setPoolPushDay(route.getPoolPushDay());
        vo.setRegionThreshold(route.getRegionThreshold());
        vo.setDirectorThreshold(route.getDirectorThreshold());
        vo.setOverPriceRule(route.getOverPriceRule());
        vo.setReplacementConfirmNames(route.getReplacementConfirmNames());
        vo.setSupplierQuoteScope(route.getSupplierQuoteScope());
        vo.setRemark(route.getRemark());
        vo.setUpdatedAt(route.getUpdatedAt());
        return vo;
    }

    private void applyApprovalSummary(ProcurementApprovalRouteVO vo, List<ApprovalLevelVO> levels) {
        List<ApprovalLevelVO> enabledLevels = levels.stream()
            .filter(level -> Objects.equals(level.getEnabled(), 1))
            .toList();
        vo.setEnabledApprovalLevelCount(enabledLevels.size());
        vo.setMissingApprovalLevelCount((int) enabledLevels.stream()
            .filter(level -> !Objects.equals(level.getSkipIfEmpty(), 1))
            .filter(level -> !StringUtils.hasText(level.getCandidateUserIds()))
            .filter(level -> !StringUtils.hasText(level.getCandidateNames()))
            .count());
        vo.setApprovalPreviewText(enabledLevels.stream()
            .limit(3)
            .map(level -> level.getLevelCode() + " " + level.getLevelName() + "：" +
                (StringUtils.hasText(level.getCandidateNames()) ? level.getCandidateNames() : "按节点规则处理"))
            .reduce((first, second) -> first + "；" + second)
            .orElse(""));
    }

    private void applySaveDTO(ProcurementApprovalRoute route, ProcurementApprovalRouteSaveDTO dto) {
        route.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        route.setRoomNature(trimToNull(dto.getRoomNature()));
        route.setRoomName(trimRequired(dto.getRoomName(), "机房不能为空"));
        route.setBuildingName(trimRequired(dto.getBuildingName(), "楼栋不能为空"));
        route.setCostCenterCode(trimToNull(dto.getCostCenterCode()));
        route.setDeptCode(trimToNull(dto.getDeptCode()));
        route.setRegionName(trimToNull(dto.getRegionName()));
        route.setDeptName(buildDepartmentName(route));
        route.setFloorLeaderNames(trimToNull(dto.getFloorLeaderNames()));
        route.setFloorLeaderRule(defaultText(dto.getFloorLeaderRule(), "任意一人通过"));
        route.setManagerNames(trimToNull(dto.getManagerNames()));
        route.setManagerRule(defaultText(dto.getManagerRule(), "任意一人通过"));
        List<Long> assistantUserIds = resolveAssistantUserIds(dto);
        route.setAssistantUserIds(joinIds(assistantUserIds));
        route.setAssistantNames(joinUserNames(assistantUserIds));
        route.setRegionManagerNames(trimToNull(dto.getRegionManagerNames()));
        route.setRegionManagerRule(defaultText(dto.getRegionManagerRule(), "任意一人通过"));
        route.setDirectorNames(trimToNull(dto.getDirectorNames()));
        route.setDirectorRule(defaultText(dto.getDirectorRule(), "任意一人通过"));
        route.setPoolPushDay(dto.getPoolPushDay() == null ? 20 : Math.min(31, Math.max(1, dto.getPoolPushDay())));
        route.setRegionThreshold(dto.getRegionThreshold() == null ? new BigDecimal("5000.00") : dto.getRegionThreshold());
        route.setDirectorThreshold(dto.getDirectorThreshold() == null ? new BigDecimal("100000.00") : dto.getDirectorThreshold());
        route.setOverPriceRule(defaultText(dto.getOverPriceRule(), "单项实际价 > 参考价 * 113%"));
        route.setReplacementConfirmNames(trimToNull(dto.getReplacementConfirmNames()));
        route.setSupplierQuoteScope(trimToNull(dto.getSupplierQuoteScope()));
        route.setRemark(trimToNull(dto.getRemark()));
        route.setRouteKey(StringUtils.hasText(dto.getRouteKey()) ? dto.getRouteKey().trim() : buildRouteKey(route));
    }

    private void ensureUniqueRouteKey(ProcurementApprovalRoute route) {
        ProcurementApprovalRoute existing = routeMapper.selectOne(new LambdaQueryWrapper<ProcurementApprovalRoute>()
            .eq(ProcurementApprovalRoute::getRouteKey, route.getRouteKey())
            .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), route.getId())) {
            throw new BizException("APPROVAL_ROUTE_DUPLICATED", "审批路由编码已存在");
        }
    }

    private String buildRouteKey(ProcurementApprovalRoute route) {
        String center = StringUtils.hasText(route.getCostCenterCode()) ? route.getCostCenterCode() : route.getDeptCode();
        return route.getRoomName() + "|" + route.getBuildingName() + "|" + (StringUtils.hasText(center) ? center : route.getDeptName());
    }

    private String buildDepartmentName(ProcurementApprovalRoute route) {
        String code = StringUtils.hasText(route.getCostCenterCode()) ? route.getCostCenterCode() : route.getDeptCode();
        String region = normalizeDepartmentRegion(route.getRegionName());
        StringBuilder name = new StringBuilder("世纪互联-业务共享中台-DC运维事业部");
        appendPart(name, region);
        appendPart(name, route.getRoomName());
        if (StringUtils.hasText(route.getBuildingName()) && !route.getBuildingName().trim().equals(route.getRoomName())) {
            appendPart(name, route.getBuildingName());
        }
        if (StringUtils.hasText(code)) {
            return "[" + code.trim() + "]" + name;
        }
        return name.toString();
    }

    private String normalizeDepartmentRegion(String regionName) {
        if (!StringUtils.hasText(regionName)) {
            return null;
        }
        String region = regionName.trim();
        if (region.endsWith("部")) {
            return region;
        }
        if (region.matches("^(华北|华东|华南|华中|西北|西南|东北).+")) {
            return region + "部";
        }
        return region;
    }

    private void appendPart(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append("-").append(value.trim());
        }
    }

    private void syncApprovalPolicy(ProcurementApprovalRoute route, ProcurementApprovalRouteSaveDTO dto) {
        OrgScope scope = ensureScope(route, dto);
        ProcurementApprovalPolicy policy = ensurePolicy(route, scope);
        List<ApprovalLevelSaveDTO> levels = dto.getApprovalLevels() == null || dto.getApprovalLevels().isEmpty()
            ? defaultLevelDtos(route)
            : dto.getApprovalLevels();
        for (ApprovalLevelSaveDTO levelDTO : levels) {
            if (levelDTO.getLevelNo() == null || levelDTO.getLevelNo() < 1 || levelDTO.getLevelNo() > 8) {
                continue;
            }
            ProcurementApprovalLevel level = levelMapper.selectOne(new LambdaQueryWrapper<ProcurementApprovalLevel>()
                .eq(ProcurementApprovalLevel::getPolicyId, policy.getId())
                .eq(ProcurementApprovalLevel::getLevelNo, levelDTO.getLevelNo())
                .last("LIMIT 1"));
            if (level == null) {
                level = new ProcurementApprovalLevel();
                level.setPolicyId(policy.getId());
                level.setLevelNo(levelDTO.getLevelNo());
            }
            level.setLevelCode(defaultText(levelDTO.getLevelCode(), "V" + levelDTO.getLevelNo()));
            level.setLevelName(defaultText(levelDTO.getLevelName(), ProcurementWorkflow.nodeNameForNode(levelDTO.getLevelNo())));
            level.setRoleCode(defaultText(levelDTO.getRoleCode(), ProcurementWorkflow.roleCodeForNode(levelDTO.getLevelNo())));
            level.setEnabled(levelDTO.getEnabled() == null ? 0 : levelDTO.getEnabled());
            level.setTriggerMode(defaultText(levelDTO.getTriggerMode(), "ALWAYS"));
            level.setPassRule(normalizePassRule(levelDTO.getPassRule()));
            level.setAmountMin(levelDTO.getAmountMin());
            level.setAmountMax(levelDTO.getAmountMax());
            level.setSkipIfEmpty(levelDTO.getSkipIfEmpty() == null ? 1 : levelDTO.getSkipIfEmpty());
            level.setSortNo(levelDTO.getLevelNo() * 10);
            level.setRemark(trimToNull(levelDTO.getRemark()));
            if (level.getId() == null) {
                levelMapper.insert(level);
            } else {
                levelMapper.updateById(level);
            }
            syncLevelUsers(level, levelDTO);
        }
    }

    private OrgScope ensureScope(ProcurementApprovalRoute route, ProcurementApprovalRouteSaveDTO dto) {
        OrgScope scope = findScope(route);
        if (scope == null) {
            scope = new OrgScope();
            scope.setScopeCode(route.getRouteKey());
            scope.setScopeType("BUILDING");
        }
        scope.setScopeName(route.getRoomName() + "/" + route.getBuildingName());
        scope.setRegionName(route.getRegionName());
        scope.setRoomName(route.getRoomName());
        scope.setBuildingName(route.getBuildingName());
        scope.setRoomNature(route.getRoomNature());
        scope.setCostCenterCode(route.getCostCenterCode());
        scope.setDeptCode(route.getDeptCode());
        scope.setDeptName(route.getDeptName());
        applyAddress(scope, dto);
        scope.setStatus(route.getEnabled() == null ? 1 : route.getEnabled());
        scope.setRemark(route.getRemark());
        if (scope.getId() == null) {
            orgScopeMapper.insert(scope);
        } else {
            orgScopeMapper.updateById(scope);
        }
        return scope;
    }

    private OrgScope findScope(ProcurementApprovalRoute route) {
        OrgScope scope = orgScopeMapper.selectOne(new LambdaQueryWrapper<OrgScope>()
            .eq(OrgScope::getScopeCode, route.getRouteKey())
            .last("LIMIT 1"));
        if (scope == null && StringUtils.hasText(route.getCostCenterCode())) {
            scope = orgScopeMapper.selectOne(new LambdaQueryWrapper<OrgScope>()
                .eq(OrgScope::getCostCenterCode, route.getCostCenterCode())
                .eq(OrgScope::getScopeType, "BUILDING")
                .last("LIMIT 1"));
        }
        return scope;
    }

    private void applyAddress(OrgScope scope, ProcurementApprovalRouteSaveDTO dto) {
        if (dto == null || !addressSubmitted(dto)) {
            return;
        }
        scope.setProvince(trimToNull(dto.getProvince()));
        scope.setCity(trimToNull(dto.getCity()));
        scope.setDistrictName(trimToNull(dto.getDistrictName()));
        scope.setTownName(trimToNull(dto.getTownName()));
        scope.setAddress(trimToNull(dto.getAddress()));
    }

    private boolean addressSubmitted(ProcurementApprovalRouteSaveDTO dto) {
        return dto.getProvince() != null
            || dto.getCity() != null
            || dto.getDistrictName() != null
            || dto.getTownName() != null
            || dto.getAddress() != null;
    }

    private void populateAddress(ProcurementApprovalRouteVO vo, OrgScope scope) {
        if (scope == null) {
            return;
        }
        vo.setProvince(scope.getProvince());
        vo.setCity(scope.getCity());
        vo.setDistrictName(scope.getDistrictName());
        vo.setTownName(scope.getTownName());
        vo.setAddress(scope.getAddress());
    }

    private ProcurementApprovalPolicy ensurePolicy(ProcurementApprovalRoute route, OrgScope scope) {
        ProcurementApprovalPolicy policy = policyMapper.selectOne(new LambdaQueryWrapper<ProcurementApprovalPolicy>()
            .eq(ProcurementApprovalPolicy::getScopeId, scope.getId())
            .last("LIMIT 1"));
        if (policy == null && StringUtils.hasText(route.getRouteKey())) {
            policy = policyMapper.selectOne(new LambdaQueryWrapper<ProcurementApprovalPolicy>()
                .eq(ProcurementApprovalPolicy::getPolicyCode, route.getRouteKey())
                .last("LIMIT 1"));
        }
        if (policy == null) {
            policy = new ProcurementApprovalPolicy();
            policy.setPolicyCode(route.getRouteKey());
        }
        policy.setScopeId(scope.getId());
        policy.setPolicyName(scope.getScopeName() + "采购审批策略");
        policy.setEnabled(route.getEnabled() == null ? 1 : route.getEnabled());
        policy.setPoolPushDay(route.getPoolPushDay());
        policy.setRegionThreshold(route.getRegionThreshold());
        policy.setDirectorThreshold(route.getDirectorThreshold());
        policy.setSkipSelfApproval(1);
        policy.setSkipDuplicateApprover(1);
        policy.setSkipLowerWhenHigherApproved(1);
        policy.setAllowSameUserMultiNode(0);
        policy.setFourEyeRequired(0);
        policy.setOverPriceRule(route.getOverPriceRule());
        policy.setRemark(route.getRemark());
        if (policy.getId() == null) {
            policyMapper.insert(policy);
        } else {
            policyMapper.updateById(policy);
        }
        return policy;
    }

    private void syncLevelUsers(ProcurementApprovalLevel level, ApprovalLevelSaveDTO dto) {
        levelUserMapper.update(null, new LambdaUpdateWrapper<ProcurementApprovalLevelUser>()
            .eq(ProcurementApprovalLevelUser::getLevelId, level.getId())
            .set(ProcurementApprovalLevelUser::getStatus, 0));
        List<Long> userIds = parseUserIds(dto.getCandidateUserIds());
        if (userIds.isEmpty()) {
            userIds = resolveUserIdsByNames(dto.getCandidateNames());
        }
        int sortNo = 1;
        for (Long userId : userIds) {
            ProcurementApprovalLevelUser levelUser = levelUserMapper.selectOne(new LambdaQueryWrapper<ProcurementApprovalLevelUser>()
                .eq(ProcurementApprovalLevelUser::getLevelId, level.getId())
                .eq(ProcurementApprovalLevelUser::getUserId, userId)
                .last("LIMIT 1"));
            if (levelUser == null) {
                levelUser = new ProcurementApprovalLevelUser();
                levelUser.setLevelId(level.getId());
                levelUser.setUserId(userId);
            }
            levelUser.setSortNo(sortNo++);
            levelUser.setStatus(1);
            if (levelUser.getId() == null) {
                levelUserMapper.insert(levelUser);
            } else {
                levelUserMapper.updateById(levelUser);
            }
        }
    }

    private List<ApprovalLevelVO> loadApprovalLevels(ProcurementApprovalRoute route) {
        OrgScope scope = findScope(route);
        if (scope == null) {
            return defaultLevelDtos(route).stream().map(this::toApprovalLevelVO).toList();
        }
        ProcurementApprovalPolicy policy = policyMapper.selectOne(new LambdaQueryWrapper<ProcurementApprovalPolicy>()
            .eq(ProcurementApprovalPolicy::getScopeId, scope.getId())
            .last("LIMIT 1"));
        if (policy == null) {
            return defaultLevelDtos(route).stream().map(this::toApprovalLevelVO).toList();
        }
        return levelMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalLevel>()
                .eq(ProcurementApprovalLevel::getPolicyId, policy.getId())
                .orderByAsc(ProcurementApprovalLevel::getLevelNo))
            .stream()
            .map(this::toApprovalLevelVO)
            .toList();
    }

    private ApprovalLevelVO toApprovalLevelVO(ProcurementApprovalLevel level) {
        ApprovalLevelVO vo = new ApprovalLevelVO();
        vo.setId(level.getId());
        vo.setLevelNo(level.getLevelNo());
        vo.setLevelCode(level.getLevelCode());
        vo.setLevelName(level.getLevelName());
        vo.setRoleCode(level.getRoleCode());
        vo.setEnabled(level.getEnabled());
        vo.setTriggerMode(level.getTriggerMode());
        vo.setPassRule(level.getPassRule());
        vo.setAmountMin(level.getAmountMin());
        vo.setAmountMax(level.getAmountMax());
        vo.setSkipIfEmpty(level.getSkipIfEmpty());
        List<Long> ids = levelUserMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalLevelUser>()
                .eq(ProcurementApprovalLevelUser::getLevelId, level.getId())
                .eq(ProcurementApprovalLevelUser::getStatus, 1)
                .orderByAsc(ProcurementApprovalLevelUser::getSortNo)
                .orderByAsc(ProcurementApprovalLevelUser::getId))
            .stream()
            .map(ProcurementApprovalLevelUser::getUserId)
            .toList();
        vo.setCandidateUserIds(joinIds(ids));
        vo.setCandidateNames(joinUserNames(ids));
        vo.setRemark(level.getRemark());
        return vo;
    }

    private ApprovalLevelVO toApprovalLevelVO(ApprovalLevelSaveDTO dto) {
        ApprovalLevelVO vo = new ApprovalLevelVO();
        vo.setLevelNo(dto.getLevelNo());
        vo.setLevelCode(dto.getLevelCode());
        vo.setLevelName(dto.getLevelName());
        vo.setRoleCode(dto.getRoleCode());
        vo.setEnabled(dto.getEnabled());
        vo.setTriggerMode(dto.getTriggerMode());
        vo.setPassRule(dto.getPassRule());
        vo.setAmountMin(dto.getAmountMin());
        vo.setAmountMax(dto.getAmountMax());
        vo.setSkipIfEmpty(dto.getSkipIfEmpty());
        vo.setCandidateNames(dto.getCandidateNames());
        vo.setRemark(dto.getRemark());
        return vo;
    }

    private String boundCandidateNames(List<ApprovalLevelVO> levels, int levelNo) {
        return levels.stream()
            .filter(level -> Objects.equals(level.getLevelNo(), levelNo))
            .filter(level -> StringUtils.hasText(level.getCandidateUserIds()))
            .map(ApprovalLevelVO::getCandidateNames)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse("");
    }

    private List<ApprovalLevelSaveDTO> defaultLevelDtos(ProcurementApprovalRoute route) {
        List<ApprovalLevelSaveDTO> levels = new ArrayList<>();
        levels.add(levelDto(1, "值班长审核", ProcurementWorkflow.ROLE_DUTY_LEADER, 0, "MANUAL", "ANY_ONE", 1, null));
        levels.add(levelDto(2, "专业工程师审核", ProcurementWorkflow.ROLE_PROFESSIONAL_ENGINEER, 1, "BY_SPECIALTY", "ANY_ONE", 1, null));
        levels.add(levelDto(3, "楼长审核", ProcurementWorkflow.ROLE_FLOOR_LEADER, 1, "ALWAYS", ruleCode(route.getFloorLeaderRule()), 0, null));
        levels.add(levelDto(4, "经理审核", ProcurementWorkflow.ROLE_MANAGER, 1, "ALWAYS", ruleCode(route.getManagerRule()), 0, null));
        ApprovalLevelSaveDTO region = levelDto(5, "区域经理审核", ProcurementWorkflow.ROLE_REGION_MANAGER, 1, "AMOUNT_GT", ruleCode(route.getRegionManagerRule()), 0, null);
        region.setAmountMin(route.getRegionThreshold());
        levels.add(region);
        ApprovalLevelSaveDTO director = levelDto(6, "总监审核", ProcurementWorkflow.ROLE_SENIOR_MANAGER, 1, "AMOUNT_GT", ruleCode(route.getDirectorRule()), 0, null);
        director.setAmountMin(route.getDirectorThreshold());
        levels.add(director);
        levels.add(levelDto(7, "V7 审核", ProcurementWorkflow.ROLE_APPROVAL_V7, 0, "MANUAL", "ANY_ONE", 1, null));
        levels.add(levelDto(8, "V8 审核", ProcurementWorkflow.ROLE_APPROVAL_V8, 0, "MANUAL", "ANY_ONE", 1, null));
        return levels;
    }

    private ApprovalLevelSaveDTO levelDto(int levelNo, String levelName, String roleCode, int enabled,
                                          String triggerMode, String passRule, int skipIfEmpty, String candidateNames) {
        ApprovalLevelSaveDTO dto = new ApprovalLevelSaveDTO();
        dto.setLevelNo(levelNo);
        dto.setLevelCode("V" + levelNo);
        dto.setLevelName(levelName);
        dto.setRoleCode(roleCode);
        dto.setEnabled(enabled);
        dto.setTriggerMode(triggerMode);
        dto.setPassRule(passRule);
        dto.setSkipIfEmpty(skipIfEmpty);
        dto.setCandidateNames(candidateNames);
        return dto;
    }

    private String normalizePassRule(String passRule) {
        return "ALL".equalsIgnoreCase(passRule) || "全部通过".equals(passRule) ? "ALL" : "ANY_ONE";
    }

    private String ruleCode(String text) {
        return StringUtils.hasText(text) && (text.contains("全部") || "ALL".equalsIgnoreCase(text)) ? "ALL" : "ANY_ONE";
    }

    private List<Long> parseUserIds(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : text.split("[,，\\s]+")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // Names are resolved separately.
            }
        }
        return ids.stream().distinct().toList();
    }

    private List<Long> resolveUserIdsByNames(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String name : text.split("[,，、/\\s]+")) {
            String normalized = name.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .and(wrapper -> wrapper.eq(SysUser::getRealName, normalized).or().eq(SysUser::getUsername, normalized))
                .last("LIMIT 1"));
            if (user != null) {
                ids.add(user.getId());
            }
        }
        return ids.stream().distinct().toList();
    }

    private List<Long> resolveAssistantUserIds(ProcurementApprovalRouteSaveDTO dto) {
        List<Long> ids = parseUserIds(dto.getAssistantUserIds());
        if (ids.isEmpty()) {
            ids = resolveUserIdsByNames(dto.getAssistantNames());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectBatchIds(ids);
        return ids.stream()
            .filter(id -> users.stream()
                .anyMatch(user -> Objects.equals(user.getId(), id)
                    && user.getStatus() != null
                    && user.getStatus() == 1
                    && !DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(user.getUserType())))
            .distinct()
            .toList();
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    private String joinUserNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<SysUser> users = userMapper.selectBatchIds(ids);
        List<String> names = new ArrayList<>();
        for (Long id : ids) {
            users.stream()
                .filter(user -> Objects.equals(user.getId(), id))
                .findFirst()
                .ifPresent(user -> names.add(userDisplayName(user)));
        }
        return String.join(",", names);
    }

    private String userDisplayName(SysUser user) {
        if (user == null) {
            return "";
        }
        String name = StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
        return name;
    }

    private String trimRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException("APPROVAL_ROUTE_FIELD_REQUIRED", message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private List<ApprovalRouteConfigItemVO> workflowPaths() {
        return List.of(
            item("STANDARD_MONTHLY", "常规月度采购", "需求人提交", "楼栋审批后进入需求池", "月度汇总后直接供应商报价", "报价后按金额进入复审"),
            item("EMERGENCY_DIRECT", "紧急采购", "需求人选择紧急采购", "楼长审核后跳过月度需求池", "直接进入供应商报价", "报价后按金额进入复审")
        );
    }

    private List<ApprovalRouteConfigItemVO> rolePermissions() {
        return List.of(
            item("V1", "值班长", "可选节点", "默认关闭", "按楼栋配置候选人", "可空"),
            item("V2", "专业工程师", "专业采购触发", "默认启用但无人时跳过", "专业类采购前置审核", "可空"),
            item("V3", "楼长", "楼长字段", "本楼栋", "需求初审", "默认任一通过"),
            item("V4", "经理", "机房负责人字段", "本楼栋", "报价复审", "默认任一通过"),
            item("ASSISTANT", "助理", "助理权限字段", "本部门/本楼栋", "查看本部门所有采购工单", "不参与审批"),
            item("V5", "区域经理", "区域负责人字段", "金额门槛", "大于5000默认进入", "金额可配置"),
            item("V6", "总监", "后台配置", "金额门槛", "启用且大于100000进入", "人员可配置"),
            item("V7/V8", "预留审批", "后台配置", "默认关闭", "后续扩展", "可空")
        );
    }

    private List<ApprovalRouteConfigItemVO> approvalRules() {
        return List.of(
            item("PASS_RULE", "多人审批规则", "每个V节点", "ANY_ONE/ALL", "任一通过或全部通过", "页面可配置"),
            item("SKIP_HIGHER", "高级人员跳级", "同一人在更高级别", "默认开启", "高级别人员已批后跳过中间下级", "用于总监/区域兼楼长场景"),
            item("ITEM_REJECT", "单项驳回", "楼长及以后", "未拒绝物品合计金额", "拒绝单项并填写原因", "被拒绝物品不计入后续金额"),
            item("OVER_13_REVIEW", "楼长超价复核", "报价阶段", "中标单价高于参考价13%", "楼长24小时内通过或单项驳回", "低于或等于13%不触发")
        );
    }

    private List<ApprovalRouteConfigItemVO> supplierPermissions() {
        return List.of(
            item("SUPPLIER_QUOTE", "供应商报价", "报价阶段", "只看推送给自己的明细", "逐项报价/放弃/替换商品", "内部人员只查看汇总"),
            item("SUPPLIER_PREPARE", "供应商备货", "审批通过后", "中标供应商", "确认备货", "不开放内部审批权限")
        );
    }

    private List<ApprovalRouteConfigItemVO> fieldMappings() {
        return List.of(
            item("floor_leader_names", "楼长", "Excel楼长", "V3", "需求初审", "可多人"),
            item("manager_names", "经理", "Excel机房负责人", "V4", "经理审核", "可多人"),
            item("region_manager_names", "区域经理", "Excel区域负责人", "V5", "金额审核", "可多人"),
            item("director_names", "总监", "后台维护", "V6", "金额审核", "可变更")
        );
    }

    private ApprovalRouteConfigItemVO item(String code, String name, String source, String scope, String permissions, String rule) {
        return new ApprovalRouteConfigItemVO(code, name, source, scope, permissions, rule, "");
    }

    private record RouteImportPlan(Long routeId, ProcurementApprovalRouteSaveDTO dto) {
    }
}
