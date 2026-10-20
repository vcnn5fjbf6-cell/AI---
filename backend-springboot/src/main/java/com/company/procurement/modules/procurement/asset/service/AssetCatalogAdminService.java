package com.company.procurement.modules.procurement.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.export.ExcelExportUtils;
import com.company.procurement.common.export.ExcelImportUtils;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.asset.dto.AssetCatalogQueryDTO;
import com.company.procurement.modules.procurement.asset.dto.AssetCatalogSaveDTO;
import com.company.procurement.modules.procurement.asset.entity.ProcurementAssetCatalogItem;
import com.company.procurement.modules.procurement.asset.mapper.ProcurementAssetCatalogItemMapper;
import com.company.procurement.modules.procurement.asset.vo.AssetCatalogItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssetCatalogAdminService {

    private static final String MANUAL_SOURCE_VERSION = "人工维护";
    private static final List<String> EXPORT_HEADERS = List.of(
        "资产大类编码", "资产大类名称", "资产名称编码", "资产名称", "折旧年限", "折旧月数",
        "单位", "盘点方式", "实物编号", "实物标签", "备注", "变更标识", "状态"
    );

    private final ProcurementAssetCatalogItemMapper catalogItemMapper;

    public PageResult<AssetCatalogItemVO> page(AssetCatalogQueryDTO query) {
        requireSuperAdmin();
        LambdaQueryWrapper<ProcurementAssetCatalogItem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(condition -> condition
                .like(ProcurementAssetCatalogItem::getCategoryCode, keyword)
                .or().like(ProcurementAssetCatalogItem::getCategoryName, keyword)
                .or().like(ProcurementAssetCatalogItem::getAssetNameCode, keyword)
                .or().like(ProcurementAssetCatalogItem::getAssetName, keyword));
        }
        if (query.getEnabled() != null) {
            wrapper.eq(ProcurementAssetCatalogItem::getEnabled, normalizeEnabled(query.getEnabled()));
        }
        wrapper.orderByAsc(ProcurementAssetCatalogItem::getCategoryCode)
            .orderByAsc(ProcurementAssetCatalogItem::getAssetNameCode);
        Page<ProcurementAssetCatalogItem> result = catalogItemMapper.selectPage(
            new Page<>(query.getPageNo(), query.getPageSize()),
            wrapper
        );
        return PageResult.of(
            result.getCurrent(),
            result.getSize(),
            result.getTotal(),
            result.getRecords().stream().map(this::toVO).toList()
        );
    }

    public void assertCanManage() {
        requireSuperAdmin();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(AssetCatalogSaveDTO dto) {
        requireSuperAdmin();
        ensureUnique(dto.getCategoryCode(), dto.getAssetNameCode(), null);
        ProcurementAssetCatalogItem item = new ProcurementAssetCatalogItem();
        apply(item, dto);
        item.setSourceVersion(MANUAL_SOURCE_VERSION);
        catalogItemMapper.insert(item);
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AssetCatalogSaveDTO dto) {
        requireSuperAdmin();
        ProcurementAssetCatalogItem item = requireItem(id);
        ensureUnique(dto.getCategoryCode(), dto.getAssetNameCode(), id);
        apply(item, dto);
        item.setSourceVersion(MANUAL_SOURCE_VERSION);
        catalogItemMapper.updateById(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Integer enabled) {
        requireSuperAdmin();
        ProcurementAssetCatalogItem item = requireItem(id);
        item.setEnabled(normalizeEnabled(enabled));
        item.setSourceVersion(MANUAL_SOURCE_VERSION);
        catalogItemMapper.updateById(item);
    }

    public ExportFile exportTemplate() {
        requireSuperAdmin();
        List<List<String>> rows = new ArrayList<>();
        rows.add(EXPORT_HEADERS);
        catalogItemMapper.selectList(
                new LambdaQueryWrapper<ProcurementAssetCatalogItem>()
                    .orderByAsc(ProcurementAssetCatalogItem::getCategoryCode)
                    .orderByAsc(ProcurementAssetCatalogItem::getAssetNameCode)
            ).forEach(item -> rows.add(toExportRow(item)));
        if (rows.size() == 1) {
            rows.add(List.of("", "", "", "", "", "", "", "", "", "", "", "", "启用"));
        }
        ExcelExportUtils.WorkbookOptions options = ExcelExportUtils.WorkbookOptions.builder()
            .freezeHeader(true)
            .autoFilter(true)
            .requiredColumns(0, 1, 2, 3, 12)
            .dropdown(12, List.of("启用", "停用"))
            .note(0, 0, "资产大类编码与资产名称编码共同构成唯一目录项。")
            .note(0, 12, "停用后，新报价不再命中该目录项；历史报价判定不变。")
            .build();
        return new ExportFile(
            "资产目录批量维护模板.xlsx",
            ExcelExportUtils.XLSX_CONTENT_TYPE,
            ExcelExportUtils.toXlsxBytes("资产目录", rows, options)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public int importTemplate(MultipartFile file) {
        requireSuperAdmin();
        List<Map<String, String>> rows = ExcelImportUtils.toHeaderMaps(ExcelImportUtils.readFirstSheet(file));
        int updated = 0;
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            if (row.values().stream().noneMatch(StringUtils::hasText)) {
                continue;
            }
            int rowNo = index + 2;
            AssetCatalogSaveDTO dto = fromImportRow(row, rowNo);
            validateImportedRow(dto, rowNo);
            ProcurementAssetCatalogItem existing = findByCode(dto.getCategoryCode(), dto.getAssetNameCode());
            if (existing == null) {
                ProcurementAssetCatalogItem item = new ProcurementAssetCatalogItem();
                apply(item, dto);
                item.setSourceVersion(MANUAL_SOURCE_VERSION);
                item.setSourceRow(rowNo);
                catalogItemMapper.insert(item);
            } else {
                apply(existing, dto);
                existing.setSourceVersion(MANUAL_SOURCE_VERSION);
                catalogItemMapper.updateById(existing);
            }
            updated++;
        }
        if (updated == 0) {
            throw new BizException("ASSET_CATALOG_IMPORT_EMPTY", "导入文件没有可维护的资产目录数据");
        }
        return updated;
    }

    private AssetCatalogSaveDTO fromImportRow(Map<String, String> row, int rowNo) {
        AssetCatalogSaveDTO dto = new AssetCatalogSaveDTO();
        dto.setCategoryCode(required(row, "资产大类编码", rowNo));
        dto.setCategoryName(required(row, "资产大类名称", rowNo));
        dto.setAssetNameCode(required(row, "资产名称编码", rowNo));
        dto.setAssetName(required(row, "资产名称", rowNo));
        dto.setDepreciationYears(text(row, "折旧年限"));
        dto.setDepreciationMonths(text(row, "折旧月数"));
        dto.setUnit(text(row, "单位"));
        dto.setInventoryMethod(text(row, "盘点方式"));
        dto.setPhysicalNumber(text(row, "实物编号"));
        dto.setPhysicalLabel(text(row, "实物标签"));
        dto.setRemark(text(row, "备注"));
        dto.setChangeMark(text(row, "变更标识"));
        String status = required(row, "状态", rowNo);
        if (!List.of("启用", "停用", "1", "0").contains(status)) {
            throw new BizException("ASSET_CATALOG_IMPORT_STATUS_INVALID", "第" + rowNo + "行状态只能填写启用或停用");
        }
        dto.setEnabled(List.of("启用", "1").contains(status) ? 1 : 0);
        return dto;
    }

    private void validateImportedRow(AssetCatalogSaveDTO dto, int rowNo) {
        requireMaxLength(dto.getCategoryCode(), 32, "资产大类编码", rowNo);
        requireMaxLength(dto.getCategoryName(), 255, "资产大类名称", rowNo);
        requireMaxLength(dto.getAssetNameCode(), 32, "资产名称编码", rowNo);
        requireMaxLength(dto.getAssetName(), 255, "资产名称", rowNo);
        requireMaxLength(dto.getDepreciationYears(), 64, "折旧年限", rowNo);
        requireMaxLength(dto.getDepreciationMonths(), 64, "折旧月数", rowNo);
        requireMaxLength(dto.getUnit(), 64, "单位", rowNo);
        requireMaxLength(dto.getInventoryMethod(), 128, "盘点方式", rowNo);
        requireMaxLength(dto.getPhysicalNumber(), 64, "实物编号", rowNo);
        requireMaxLength(dto.getPhysicalLabel(), 128, "实物标签", rowNo);
        requireMaxLength(dto.getRemark(), 1000, "备注", rowNo);
        requireMaxLength(dto.getChangeMark(), 255, "变更标识", rowNo);
    }

    private void requireMaxLength(String value, int maxLength, String label, int rowNo) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(
                "ASSET_CATALOG_IMPORT_LENGTH_INVALID",
                "第" + rowNo + "行" + label + "不能超过" + maxLength + "个字符"
            );
        }
    }

    private void apply(ProcurementAssetCatalogItem item, AssetCatalogSaveDTO dto) {
        item.setCategoryCode(trim(dto.getCategoryCode()));
        item.setCategoryName(trim(dto.getCategoryName()));
        item.setAssetNameCode(trim(dto.getAssetNameCode()));
        item.setAssetName(trim(dto.getAssetName()));
        item.setDepreciationYears(trim(dto.getDepreciationYears()));
        item.setDepreciationMonths(trim(dto.getDepreciationMonths()));
        item.setUnit(trim(dto.getUnit()));
        item.setInventoryMethod(trim(dto.getInventoryMethod()));
        item.setPhysicalNumber(trim(dto.getPhysicalNumber()));
        item.setPhysicalLabel(trim(dto.getPhysicalLabel()));
        item.setRemark(trim(dto.getRemark()));
        item.setChangeMark(trim(dto.getChangeMark()));
        item.setEnabled(normalizeEnabled(dto.getEnabled()));
    }

    private void ensureUnique(String categoryCode, String assetNameCode, Long excludedId) {
        ProcurementAssetCatalogItem existing = findByCode(categoryCode, assetNameCode);
        if (existing != null && !existing.getId().equals(excludedId)) {
            throw new BizException("ASSET_CATALOG_CODE_DUPLICATED", "资产大类编码与资产名称编码已存在");
        }
    }

    private ProcurementAssetCatalogItem findByCode(String categoryCode, String assetNameCode) {
        return catalogItemMapper.selectOne(
            new LambdaQueryWrapper<ProcurementAssetCatalogItem>()
                .eq(ProcurementAssetCatalogItem::getCategoryCode, trim(categoryCode))
                .eq(ProcurementAssetCatalogItem::getAssetNameCode, trim(assetNameCode))
                .last("LIMIT 1")
        );
    }

    private ProcurementAssetCatalogItem requireItem(Long id) {
        ProcurementAssetCatalogItem item = id == null ? null : catalogItemMapper.selectById(id);
        if (item == null) {
            throw new BizException("ASSET_CATALOG_NOT_FOUND", "资产目录项不存在或已删除");
        }
        return item;
    }

    private int normalizeEnabled(Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new BizException("ASSET_CATALOG_STATUS_INVALID", "资产目录状态只能为启用或停用");
        }
        return enabled;
    }

    private void requireSuperAdmin() {
        if (!DataScopeHelper.isSuperAdmin(AuthContextHolder.requireUser())) {
            throw new BizException("FORBIDDEN", "只有最高管理员可以维护资产目录");
        }
    }

    private AssetCatalogItemVO toVO(ProcurementAssetCatalogItem item) {
        AssetCatalogItemVO vo = new AssetCatalogItemVO();
        vo.setId(item.getId());
        vo.setCategoryCode(item.getCategoryCode());
        vo.setCategoryName(item.getCategoryName());
        vo.setAssetNameCode(item.getAssetNameCode());
        vo.setAssetName(item.getAssetName());
        vo.setDepreciationYears(item.getDepreciationYears());
        vo.setDepreciationMonths(item.getDepreciationMonths());
        vo.setUnit(item.getUnit());
        vo.setInventoryMethod(item.getInventoryMethod());
        vo.setPhysicalNumber(item.getPhysicalNumber());
        vo.setPhysicalLabel(item.getPhysicalLabel());
        vo.setRemark(item.getRemark());
        vo.setChangeMark(item.getChangeMark());
        vo.setEnabled(item.getEnabled());
        vo.setSourceVersion(item.getSourceVersion());
        vo.setSourceRow(item.getSourceRow());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private List<String> toExportRow(ProcurementAssetCatalogItem item) {
        return List.of(
            text(item.getCategoryCode()), text(item.getCategoryName()), text(item.getAssetNameCode()), text(item.getAssetName()),
            text(item.getDepreciationYears()), text(item.getDepreciationMonths()), text(item.getUnit()), text(item.getInventoryMethod()),
            text(item.getPhysicalNumber()), text(item.getPhysicalLabel()), text(item.getRemark()), text(item.getChangeMark()),
            item.getEnabled() != null && item.getEnabled() == 1 ? "启用" : "停用"
        );
    }

    private String required(Map<String, String> row, String header, int rowNo) {
        String value = text(row, header);
        if (!StringUtils.hasText(value)) {
            throw new BizException("ASSET_CATALOG_IMPORT_REQUIRED", "第" + rowNo + "行" + header + "不能为空");
        }
        return value;
    }

    private String text(Map<String, String> row, String header) {
        return trim(row.get(ExcelImportUtils.normalizeHeader(header)));
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
