package com.company.procurement.modules.procurement.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.procurement.asset.entity.ProcurementAssetCatalogItem;
import com.company.procurement.modules.procurement.asset.mapper.ProcurementAssetCatalogItemMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AssetCatalogService {

    private static final String CATALOG_RESOURCE = "procurement/asset-catalog-v2.1.json";

    private final ProcurementAssetCatalogItemMapper catalogItemMapper;
    private final CatalogFile fallbackCatalog;

    @Autowired
    public AssetCatalogService(ProcurementAssetCatalogItemMapper catalogItemMapper, ObjectMapper objectMapper) {
        this.catalogItemMapper = catalogItemMapper;
        this.fallbackCatalog = readCatalog(objectMapper);
    }

    AssetCatalogService(ObjectMapper objectMapper) {
        this.catalogItemMapper = null;
        this.fallbackCatalog = readCatalog(objectMapper);
    }

    @PostConstruct
    public void seedMissingCatalogEntries() {
        if (catalogItemMapper == null) {
            return;
        }
        try {
            Map<String, ProcurementAssetCatalogItem> existingByCode = new LinkedHashMap<>();
            for (ProcurementAssetCatalogItem item : catalogItemMapper.selectList(new LambdaQueryWrapper<>())) {
                existingByCode.put(catalogKey(item.getCategoryCode(), item.getAssetNameCode()), item);
            }
            int inserted = 0;
            for (AssetCatalogEntry entry : fallbackCatalog.getEntries()) {
                if (existingByCode.containsKey(catalogKey(entry.getCategoryCode(), entry.getAssetNameCode()))) {
                    continue;
                }
                catalogItemMapper.insert(toEntity(entry, fallbackCatalog.getVersion()));
                inserted++;
            }
            if (inserted > 0) {
                log.info("Seeded {} missing asset catalog entries from {}", inserted, fallbackCatalog.getVersion());
            }
        } catch (RuntimeException ex) {
            log.warn("Managed asset catalog is not available; packaged catalog remains active", ex);
        }
    }

    public String version() {
        if (catalogItemMapper == null) {
            return fallbackCatalog.getVersion();
        }
        try {
            List<ProcurementAssetCatalogItem> entries = catalogItemMapper.selectList(
                new LambdaQueryWrapper<ProcurementAssetCatalogItem>()
                    .select(ProcurementAssetCatalogItem::getSourceVersion)
                    .orderByDesc(ProcurementAssetCatalogItem::getUpdatedAt)
                    .last("LIMIT 1")
            );
            if (!entries.isEmpty() && StringUtils.hasText(entries.get(0).getSourceVersion())) {
                return entries.get(0).getSourceVersion();
            }
            return "MANAGED_ASSET_CATALOG";
        } catch (RuntimeException ex) {
            return fallbackCatalog.getVersion();
        }
    }

    public List<AssetCatalogEntry> activeEntries() {
        if (catalogItemMapper == null) {
            return fallbackActiveEntries();
        }
        try {
            return catalogItemMapper.selectList(
                    new LambdaQueryWrapper<ProcurementAssetCatalogItem>()
                        .eq(ProcurementAssetCatalogItem::getEnabled, 1)
                        .orderByAsc(ProcurementAssetCatalogItem::getCategoryCode)
                        .orderByAsc(ProcurementAssetCatalogItem::getAssetNameCode)
                ).stream()
                .map(this::toEntry)
                .toList();
        } catch (RuntimeException ex) {
            log.warn("Cannot read managed asset catalog; using packaged fallback", ex);
            return fallbackActiveEntries();
        }
    }

    public Optional<AssetCatalogEntry> find(String categoryCode, String assetNameCode) {
        if (!StringUtils.hasText(categoryCode) || !StringUtils.hasText(assetNameCode)) {
            return Optional.empty();
        }
        String expectedKey = catalogKey(categoryCode, assetNameCode);
        return activeEntries().stream()
            .filter(entry -> expectedKey.equals(catalogKey(entry.getCategoryCode(), entry.getAssetNameCode())))
            .findFirst();
    }

    public Optional<AssetCatalogEntry> matchExact(String itemName, String specModel) {
        String normalizedProduct = normalize(firstText(itemName, "") + firstText(specModel, ""));
        if (!StringUtils.hasText(normalizedProduct)) {
            return Optional.empty();
        }
        return activeEntries().stream()
            .filter(entry -> {
                String normalizedAssetName = normalize(entry.getAssetName());
                return normalizedAssetName.length() >= 2 && normalizedProduct.contains(normalizedAssetName);
            })
            .max(Comparator.comparingInt(entry -> normalize(entry.getAssetName()).length()));
    }

    public String promptText() {
        StringBuilder builder = new StringBuilder();
        for (AssetCatalogEntry entry : activeEntries()) {
            builder.append(entry.getCategoryCode()).append('|')
                .append(entry.getAssetNameCode()).append('|')
                .append(entry.getAssetName()).append('|')
                .append(entry.getCategoryName());
            if (StringUtils.hasText(entry.getRemark())) {
                builder.append('|').append(entry.getRemark());
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private List<AssetCatalogEntry> fallbackActiveEntries() {
        return fallbackCatalog.getEntries().stream().filter(AssetCatalogEntry::isActive).toList();
    }

    private ProcurementAssetCatalogItem toEntity(AssetCatalogEntry entry, String sourceVersion) {
        ProcurementAssetCatalogItem item = new ProcurementAssetCatalogItem();
        item.setCategoryCode(entry.getCategoryCode());
        item.setCategoryName(entry.getCategoryName());
        item.setAssetNameCode(entry.getAssetNameCode());
        item.setAssetName(entry.getAssetName());
        item.setDepreciationYears(entry.getDepreciationYears());
        item.setDepreciationMonths(entry.getDepreciationMonths());
        item.setUnit(entry.getUnit());
        item.setInventoryMethod(entry.getInventoryMethod());
        item.setPhysicalNumber(entry.getPhysicalNumber());
        item.setPhysicalLabel(entry.getPhysicalLabel());
        item.setRemark(entry.getRemark());
        item.setChangeMark(entry.getChangeMark());
        item.setEnabled(entry.isActive() ? 1 : 0);
        item.setSourceVersion(sourceVersion);
        item.setSourceRow(entry.getSourceRow());
        return item;
    }

    private AssetCatalogEntry toEntry(ProcurementAssetCatalogItem item) {
        AssetCatalogEntry entry = new AssetCatalogEntry();
        entry.setCategoryCode(item.getCategoryCode());
        entry.setCategoryName(item.getCategoryName());
        entry.setAssetNameCode(item.getAssetNameCode());
        entry.setAssetName(item.getAssetName());
        entry.setDepreciationYears(item.getDepreciationYears());
        entry.setDepreciationMonths(item.getDepreciationMonths());
        entry.setUnit(item.getUnit());
        entry.setInventoryMethod(item.getInventoryMethod());
        entry.setPhysicalNumber(item.getPhysicalNumber());
        entry.setPhysicalLabel(item.getPhysicalLabel());
        entry.setRemark(item.getRemark());
        entry.setChangeMark(item.getChangeMark());
        entry.setActive(item.getEnabled() != null && item.getEnabled() == 1);
        entry.setSourceRow(item.getSourceRow());
        return entry;
    }

    private CatalogFile readCatalog(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            CatalogFile catalog = objectMapper.readValue(inputStream, CatalogFile.class);
            if (catalog.getEntries() == null || catalog.getEntries().isEmpty()) {
                throw new IllegalStateException("Asset catalog is empty: " + CATALOG_RESOURCE);
            }
            return catalog;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read asset catalog: " + CATALOG_RESOURCE, ex);
        }
    }

    private String catalogKey(String categoryCode, String assetNameCode) {
        return firstText(categoryCode, "").trim() + ':' + firstText(assetNameCode, "").trim();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]", "");
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CatalogFile {
        private String version;
        private List<AssetCatalogEntry> entries;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetCatalogEntry {
        private String categoryCode;
        private String categoryName;
        private String assetNameCode;
        private String assetName;
        private String depreciationYears;
        private String depreciationMonths;
        private String unit;
        private String inventoryMethod;
        private String physicalNumber;
        private String physicalLabel;
        private String remark;
        private String changeMark;
        private boolean active;
        private Integer sourceRow;
    }
}
