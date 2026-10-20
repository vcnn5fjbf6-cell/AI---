package com.company.procurement.modules.ehs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.ehs.dto.EhsRegistryQueryDTO;
import com.company.procurement.modules.ehs.dto.EhsRegistrySaveDTO;
import com.company.procurement.modules.ehs.entity.EhsRegistryRecord;
import com.company.procurement.modules.ehs.mapper.EhsRegistryRecordMapper;
import com.company.procurement.modules.ehs.vo.EhsRegistryStatsVO;
import com.company.procurement.modules.ehs.vo.EhsRegistryVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EhsRegistryService {

    private static final Set<String> MODULES = Set.of("HAZARD", "WASTE", "PPE", "INCIDENT");
    private static final Map<String, Set<String>> STATUSES = Map.of(
        "HAZARD", Set.of("OPEN", "RECTIFYING", "VERIFYING", "CLOSED"),
        "WASTE", Set.of("STORED", "PENDING_TRANSFER", "TRANSFERRED"),
        "PPE", Set.of("NORMAL", "LOW_STOCK", "EXPIRING"),
        "INCIDENT", Set.of("OPEN", "INVESTIGATING", "RECTIFYING", "CLOSED")
    );
    private static final Map<String, String> PREFIXES = Map.of(
        "HAZARD", "HD",
        "WASTE", "HW",
        "PPE", "PP",
        "INCIDENT", "IN"
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final EhsRegistryRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    public PageResult<EhsRegistryVO> page(EhsRegistryQueryDTO query) {
        String module = normalizeModule(query.getModuleType());
        LambdaQueryWrapper<EhsRegistryRecord> wrapper = new LambdaQueryWrapper<EhsRegistryRecord>()
            .eq(EhsRegistryRecord::getModuleType, module)
            .orderByDesc(EhsRegistryRecord::getUpdatedAt)
            .orderByDesc(EhsRegistryRecord::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item.like(EhsRegistryRecord::getRecordNo, keyword)
                .or().like(EhsRegistryRecord::getTitle, keyword)
                .or().like(EhsRegistryRecord::getBuilding, keyword)
                .or().like(EhsRegistryRecord::getLocation, keyword)
                .or().like(EhsRegistryRecord::getOwnerName, keyword)
                .or().like(EhsRegistryRecord::getHandlerName, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(EhsRegistryRecord::getStatus, normalizeStatus(module, query.getStatus()));
        }
        if (StringUtils.hasText(query.getRiskLevel())) {
            wrapper.eq(EhsRegistryRecord::getRiskLevel, query.getRiskLevel().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getBuilding())) {
            wrapper.eq(EhsRegistryRecord::getBuilding, query.getBuilding().trim());
        }
        Page<EhsRegistryRecord> page = recordMapper.selectPage(
            new Page<>(query.getPageNo(), query.getPageSize()),
            wrapper
        );
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream()
            .map(this::toVO)
            .toList());
    }

    public EhsRegistryVO detail(Long id) {
        return toVO(requireRecord(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(EhsRegistrySaveDTO dto) {
        EhsRegistryRecord record = new EhsRegistryRecord();
        apply(record, dto);
        record.setRecordNo(nextNo(record.getModuleType()));
        recordMapper.insert(record);
        return record.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EhsRegistrySaveDTO dto) {
        EhsRegistryRecord existing = requireRecord(id);
        String module = normalizeModule(dto.getModuleType());
        if (!module.equals(existing.getModuleType())) {
            throw new BizException("EHS_MODULE_CHANGE_FORBIDDEN", "台账记录不能跨模块修改");
        }
        apply(existing, dto);
        recordMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (recordMapper.deleteById(requireRecord(id)) < 1) {
            throw new BizException("EHS_RECORD_DELETE_FAILED", "台账记录删除失败");
        }
    }

    public EhsRegistryStatsVO stats(String moduleType) {
        String module = normalizeModule(moduleType);
        List<EhsRegistryRecord> records = listByModule(module);
        long alerts = records.stream().filter(this::isAlert).count();
        long completed = "PPE".equals(module)
            ? records.stream().filter(record -> "NORMAL".equals(record.getStatus())).count()
            : records.stream().filter(this::isCompleted).count();
        long active = "PPE".equals(module) ? alerts : records.size() - completed;
        Map<String, Long> statusCounts = records.stream().collect(Collectors.groupingBy(
            EhsRegistryRecord::getStatus,
            LinkedHashMap::new,
            Collectors.counting()
        ));
        Map<String, Long> categoryCounts = records.stream()
            .filter(record -> StringUtils.hasText(record.getCategory()))
            .collect(Collectors.groupingBy(
                EhsRegistryRecord::getCategory,
                LinkedHashMap::new,
                Collectors.counting()
            ));
        return EhsRegistryStatsVO.builder()
            .totalCount(records.size())
            .activeCount(active)
            .completedCount(completed)
            .alertCount(alerts)
            .statusCounts(statusCounts)
            .categoryCounts(categoryCounts)
            .build();
    }

    public List<EhsRegistryRecord> listByModule(String moduleType) {
        String module = normalizeModule(moduleType);
        return recordMapper.selectList(new LambdaQueryWrapper<EhsRegistryRecord>()
            .eq(EhsRegistryRecord::getModuleType, module)
            .orderByDesc(EhsRegistryRecord::getUpdatedAt)
            .orderByDesc(EhsRegistryRecord::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public int importRows(String moduleType, List<List<String>> rows) {
        String module = normalizeModule(moduleType);
        if (rows == null || rows.size() < 2) {
            return 0;
        }
        Map<String, Integer> headers = headerIndexes(rows.get(0));
        int imported = 0;
        for (List<String> row : rows.subList(1, rows.size())) {
            if (row == null || row.stream().noneMatch(StringUtils::hasText)) {
                continue;
            }
            EhsRegistrySaveDTO dto = new EhsRegistrySaveDTO();
            dto.setModuleType(module);
            dto.setTitle(cell(row, headers, "名称"));
            dto.setCategory(cell(row, headers, "分类"));
            dto.setSubType(cell(row, headers, "子类型"));
            dto.setBuilding(cell(row, headers, "楼栋"));
            dto.setLocation(cell(row, headers, "位置"));
            dto.setRiskLevel(cell(row, headers, "等级"));
            dto.setStatus(cell(row, headers, "状态"));
            dto.setOwnerName(cell(row, headers, "责任人"));
            dto.setHandlerName(cell(row, headers, "经办人"));
            dto.setQuantity(decimal(cell(row, headers, "数量")));
            dto.setUnit(cell(row, headers, "单位"));
            dto.setOccurredAt(dateTime(cell(row, headers, "发生时间")));
            dto.setDeadlineAt(dateTime(cell(row, headers, "截止时间")));
            dto.setRemark(cell(row, headers, "备注"));
            String detailJson = cell(row, headers, "扩展信息(JSON)");
            if (StringUtils.hasText(detailJson)) {
                dto.setDetails(readMap(detailJson));
            }
            if (!StringUtils.hasText(dto.getTitle()) || !StringUtils.hasText(dto.getStatus())) {
                throw new BizException("EHS_IMPORT_ROW_INVALID", "导入行的名称和状态不能为空");
            }
            create(dto);
            imported++;
        }
        return imported;
    }

    public List<List<String>> exportRows(String moduleType) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("编号", "名称", "分类", "子类型", "楼栋", "位置", "等级", "状态", "责任人", "经办人", "数量", "单位", "发生时间", "截止时间", "完成时间", "备注", "扩展信息(JSON)"));
        for (EhsRegistryRecord record : listByModule(moduleType)) {
            rows.add(List.of(
                text(record.getRecordNo()), text(record.getTitle()), text(record.getCategory()), text(record.getSubType()),
                text(record.getBuilding()), text(record.getLocation()), text(record.getRiskLevel()), text(record.getStatus()),
                text(record.getOwnerName()), text(record.getHandlerName()), text(record.getQuantity()), text(record.getUnit()),
                text(record.getOccurredAt()), text(record.getDeadlineAt()), text(record.getCompletedAt()), text(record.getRemark()),
                text(record.getDetailJson())
            ));
        }
        return rows;
    }

    public List<List<String>> importTemplate(String moduleType) {
        String module = normalizeModule(moduleType);
        return List.of(
            List.of("名称", "分类", "子类型", "楼栋", "位置", "等级", "状态", "责任人", "经办人", "数量", "单位", "发生时间", "截止时间", "备注", "扩展信息(JSON)"),
            List.of("示例记录", "示例分类", "", "1号楼", "一层", "MEDIUM", defaultStatus(module), "张三", "李四", "1", "项", "2026-07-20 09:00:00", "2026-07-23 18:00:00", "", "{}")
        );
    }

    private void apply(EhsRegistryRecord record, EhsRegistrySaveDTO dto) {
        String module = normalizeModule(dto.getModuleType());
        String status = normalizeStatus(module, dto.getStatus());
        record.setModuleType(module);
        record.setTitle(limit(dto.getTitle(), 255));
        record.setCategory(limit(dto.getCategory(), 128));
        record.setSubType(limit(dto.getSubType(), 64));
        record.setBuilding(limit(dto.getBuilding(), 128));
        record.setLocation(limit(dto.getLocation(), 255));
        record.setRiskLevel(normalizeOptionalCode(dto.getRiskLevel()));
        record.setStatus(status);
        record.setOwnerUserId(dto.getOwnerUserId());
        record.setOwnerName(limit(dto.getOwnerName(), 128));
        record.setHandlerName(limit(dto.getHandlerName(), 128));
        record.setQuantity(dto.getQuantity());
        record.setUnit(limit(dto.getUnit(), 32));
        record.setOccurredAt(dto.getOccurredAt());
        record.setDeadlineAt(dto.getDeadlineAt());
        record.setCompletedAt(isCompleted(module, status)
            ? (dto.getCompletedAt() == null ? LocalDateTime.now() : dto.getCompletedAt())
            : null);
        record.setDetailJson(writeJson(dto.getDetails()));
        record.setRemark(limit(dto.getRemark(), 1000));
    }

    private EhsRegistryRecord requireRecord(Long id) {
        EhsRegistryRecord record = id == null ? null : recordMapper.selectById(id);
        if (record == null) {
            throw new BizException("EHS_RECORD_NOT_FOUND", "台账记录不存在");
        }
        return record;
    }

    private EhsRegistryVO toVO(EhsRegistryRecord record) {
        EhsRegistryVO vo = new EhsRegistryVO();
        BeanUtils.copyProperties(record, vo);
        vo.setDetails(readMap(record.getDetailJson()));
        return vo;
    }

    private boolean isCompleted(EhsRegistryRecord record) {
        return isCompleted(record.getModuleType(), record.getStatus());
    }

    private boolean isCompleted(String module, String status) {
        return ("HAZARD".equals(module) || "INCIDENT".equals(module))
            ? "CLOSED".equals(status)
            : "WASTE".equals(module) && "TRANSFERRED".equals(status);
    }

    private boolean isAlert(EhsRegistryRecord record) {
        if ("PPE".equals(record.getModuleType())) {
            return Set.of("LOW_STOCK", "EXPIRING").contains(record.getStatus());
        }
        if ("WASTE".equals(record.getModuleType())) {
            return "PENDING_TRANSFER".equals(record.getStatus());
        }
        return !isCompleted(record) && record.getDeadlineAt() != null
            && !record.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(3));
    }

    private String normalizeModule(String value) {
        String module = normalizeCode(value);
        if (!MODULES.contains(module)) {
            throw new BizException("EHS_MODULE_INVALID", "不支持的 EHS 台账模块");
        }
        return module;
    }

    private String normalizeStatus(String module, String value) {
        String status = normalizeCode(value);
        if (!STATUSES.get(module).contains(status)) {
            throw new BizException("EHS_STATUS_INVALID", "当前模块不支持该状态");
        }
        return status;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalCode(String value) {
        String code = normalizeCode(value);
        return code.isEmpty() ? null : code;
    }

    private String defaultStatus(String module) {
        return switch (module) {
            case "WASTE" -> "STORED";
            case "PPE" -> "NORMAL";
            default -> "OPEN";
        };
    }

    private String nextNo(String module) {
        return PREFIXES.get(module)
            + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
            + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new BizException("EHS_JSON_INVALID", "扩展信息格式不正确");
        }
    }

    private Map<String, Object> readMap(String value) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Integer> headerIndexes(List<String> row) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; row != null && index < row.size(); index++) {
            if (StringUtils.hasText(row.get(index))) {
                result.put(row.get(index).trim(), index);
            }
        }
        return result;
    }

    private String cell(List<String> row, Map<String, Integer> headers, String name) {
        Integer index = headers.get(name);
        return index == null || index >= row.size() || row.get(index) == null ? "" : row.get(index).trim();
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new BizException("EHS_IMPORT_NUMBER_INVALID", "导入数量必须是数字");
        }
    }

    private LocalDateTime dateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )) {
            try {
                return LocalDateTime.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        throw new BizException("EHS_IMPORT_DATE_INVALID", "导入时间格式应为 yyyy-MM-dd HH:mm:ss");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
