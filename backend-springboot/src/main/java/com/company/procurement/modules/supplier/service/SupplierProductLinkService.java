package com.company.procurement.modules.supplier.service;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.supplier.entity.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SupplierProductLinkService {

    public static final String RULE_XFS_INTERNAL_SKU = "XFS_INTERNAL_SKU";

    private static final Pattern INTERNAL_PRODUCT_CODE_PATTERN = Pattern.compile("^\\d{1,32}$");
    private static final String XFS_NAME_PREFIX = "鑫方盛";
    private static final String XFS_PRODUCT_URL_PREFIX = "https://vip.xfs.com/vip/productsku/";

    private final EcommerceUrlService ecommerceUrlService;

    public String ruleCode(Supplier supplier) {
        return isXfsSupplier(supplier) ? RULE_XFS_INTERNAL_SKU : null;
    }

    public String normalize(String rawValue, Supplier supplier) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (INTERNAL_PRODUCT_CODE_PATTERN.matcher(value).matches()) {
            if (isXfsSupplier(supplier)) {
                return XFS_PRODUCT_URL_PREFIX + value + ".html";
            }
            throw new BizException(
                "ECOMMERCE_URL_INVALID",
                "当前供应商未配置内部商品编号规则，请填写商品详情页链接"
            );
        }
        return ecommerceUrlService.normalize(value);
    }

    public String normalizeForLine(String rawValue, Supplier supplier, int lineNo) {
        try {
            return normalize(rawValue, supplier);
        } catch (BizException ex) {
            throw new BizException(ex.getCode(), "第 " + lineNo + " 行替换商品链接：" + ex.getMessage());
        }
    }

    private boolean isXfsSupplier(Supplier supplier) {
        if (supplier == null) {
            return false;
        }
        if ("XFS".equals(normalizeCode(supplier.getSupplierCode()))) {
            return true;
        }
        return normalizeName(supplier.getSupplierName()).startsWith(XFS_NAME_PREFIX)
            || normalizeName(supplier.getSupplierShortName()).startsWith(XFS_NAME_PREFIX);
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeName(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("\\s+", "") : "";
    }
}
