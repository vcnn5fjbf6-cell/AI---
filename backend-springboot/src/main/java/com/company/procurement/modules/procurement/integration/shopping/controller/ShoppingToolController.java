package com.company.procurement.modules.procurement.integration.shopping.controller;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.procurement.integration.shopping.config.ShoppingToolProperties;
import com.company.procurement.modules.procurement.integration.shopping.dto.ShoppingSearchDTO;
import com.company.procurement.modules.procurement.integration.shopping.vo.ShoppingProductVO;
import com.company.procurement.modules.procurement.integration.shopping.vo.ShoppingSearchResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/integrations/shopping")
@RequiredArgsConstructor
public class ShoppingToolController {

    private static final String PROVIDER_JD_ENTERPRISE = "JD_ENTERPRISE";
    private static final String PROVIDER_1688 = "1688";
    private static final List<String> DEFAULT_PLATFORMS = List.of(PROVIDER_JD_ENTERPRISE, PROVIDER_1688);

    private final ShoppingToolProperties properties;

    @PostMapping("/search")
    public Result<ShoppingSearchResultVO> search(@RequestBody @Valid ShoppingSearchDTO dto, HttpServletRequest request) {
        verifyToken(request);

        String keyword = dto.getKeyword().trim();
        String category = normalizeCategory(dto.getCategory());
        int maxResults = Math.max(1, Math.min(dto.getMaxResults() == null ? 5 : dto.getMaxResults(), 10));
        List<String> platforms = normalizePlatforms(dto.getPlatforms());

        ShoppingSearchResultVO result = new ShoppingSearchResultVO();
        result.setKeyword(keyword);
        result.setCategory(category);
        result.setMockData(true);
        result.setSummary("演示数据：已按 1688 和京东企业购生成候选商品。价格、库存、供货周期均为 mock，正式接入后替换为官方 API 返回。");

        List<ShoppingProductVO> products = new ArrayList<>();
        int variant = 0;
        while (products.size() < maxResults && variant < 5) {
            for (String platform : platforms) {
                products.add(buildMockCandidate(keyword, category, platform, variant));
                if (products.size() >= maxResults) {
                    break;
                }
            }
            variant++;
        }
        result.setProducts(products);
        return Result.ok(result);
    }

    private void verifyToken(HttpServletRequest request) {
        if (!StringUtils.hasText(properties.getToken())) {
            throw new BizException("SHOPPING_TOOL_UNAUTHORIZED", "Shopping tool token is not configured");
        }
        String expected = properties.getToken().trim();
        String actual = request.getHeader("X-Shopping-Tool-Token");
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(actual) && StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            actual = authorization.substring("Bearer ".length()).trim();
        }
        if (!expected.equals(actual)) {
            throw new BizException("SHOPPING_TOOL_UNAUTHORIZED", "Shopping tool token invalid");
        }
    }

    private ShoppingProductVO buildMockCandidate(String keyword, String category, String platform, int variant) {
        MockProduct mock = resolveMockProduct(keyword, platform, variant);
        ShoppingProductVO product = new ShoppingProductVO();
        product.setProviderCode(platform);
        product.setSkuId(mockSku(platform, keyword, variant));
        product.setItemName(mock.itemName());
        product.setSpecModel(mock.specModel());
        product.setUnit("件");
        product.setQty(BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP));
        product.setUnitPrice(mock.unitPrice().setScale(2, RoundingMode.HALF_UP));
        product.setEcommercePlatform(platformLabel(platform));
        product.setSupplierName(mock.supplierName());
        product.setStockStatus(mock.stockStatus());
        product.setDeliveryLeadTime(mock.deliveryLeadTime());
        product.setEcommerceUrl(searchUrl(keyword, platform));
        product.setImageUrl("");
        product.setPurchaseReason("演示推荐：" + platformLabel(platform) + "可用于" + category + "采购比价，满足“" + keyword + "”需求。");
        product.setScore(mock.score());
        return product;
    }

    private MockProduct resolveMockProduct(String keyword, String platform, int variant) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        boolean jd = PROVIDER_JD_ENTERPRISE.equals(platform);
        int offset = Math.max(0, variant);
        if (keyword.contains("鼠标") || lower.contains("mouse")) {
            return new MockProduct(
                jd ? "京东企业购 罗技 M650 无线鼠标" : "1688 批采 静音无线鼠标",
                jd ? "M650 / 蓝牙+2.4G / 企业采购装" : "2.4G无线 / 静音按键 / 批量采购",
                price(jd ? 129 : 42, offset, jd ? 12 : 4),
                jd ? "京东企业购自营" : "深圳市优选办公用品有限公司",
                jd ? "现货" : "支持批量现货",
                jd ? "预计1-2天送达" : "预计3-5天发货",
                new BigDecimal(jd ? "0.94" : "0.86")
            );
        }
        if (keyword.contains("键盘") || lower.contains("keyboard")) {
            return new MockProduct(
                jd ? "京东企业购 罗技 K580 办公键盘" : "1688 办公无线键盘批采款",
                jd ? "K580 / 多设备连接 / 企业采购装" : "无线薄膜键盘 / 支持LOGO定制",
                price(jd ? 179 : 55, offset, jd ? 15 : 6),
                jd ? "京东企业购自营" : "东莞市办公外设工厂店",
                jd ? "现货" : "500件内现货",
                jd ? "预计1-2天送达" : "预计3-5天发货",
                new BigDecimal(jd ? "0.92" : "0.84")
            );
        }
        if (keyword.contains("交换机") || lower.contains("switch")) {
            return new MockProduct(
                jd ? "京东企业购 8口千兆交换机" : "1688 千兆交换机工程批发款",
                jd ? "8口千兆 / 金属机身 / 企业网络" : "8口千兆 / 工程项目批采",
                price(jd ? 189 : 118, offset, jd ? 20 : 12),
                jd ? "京东企业购自营" : "深圳市网络设备源头工厂",
                jd ? "现货" : "支持项目批量",
                jd ? "预计1-2天送达" : "预计3-6天发货",
                new BigDecimal(jd ? "0.91" : "0.85")
            );
        }
        if (keyword.contains("ups") || keyword.contains("UPS")) {
            return new MockProduct(
                jd ? "京东企业购 在线式 UPS 电源" : "1688 UPS 电源工程采购款",
                jd ? "1KVA / 在线式 / 含基础质保" : "1KVA / 在线式 / 支持项目报价",
                price(jd ? 1580 : 980, offset, jd ? 120 : 80),
                jd ? "京东企业购工业品" : "佛山市电源设备供应商",
                jd ? "部分仓现货" : "需确认库存",
                jd ? "预计2-4天送达" : "预计5-7天发货",
                new BigDecimal(jd ? "0.89" : "0.83")
            );
        }
        return new MockProduct(
            jd ? "京东企业购 " + keyword + " 企业采购候选款" : "1688 " + keyword + " 批量采购候选款",
            jd ? "企业采购标准款 / 支持开票" : "工厂批采款 / 支持阶梯报价",
            price(jd ? 299 : 168, offset, jd ? 30 : 18),
            jd ? "京东企业购自营" : "1688认证供应商",
            jd ? "现货" : "需确认库存",
            jd ? "预计1-3天送达" : "预计3-7天发货",
            new BigDecimal(jd ? "0.88" : "0.82")
        );
    }

    private BigDecimal price(int base, int variant, int step) {
        return BigDecimal.valueOf((long) base + (long) variant * step);
    }

    private String mockSku(String platform, String keyword, int variant) {
        String normalized = keyword.replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5]", "");
        int hash = Math.abs((normalized + platform + variant).hashCode());
        return "MOCK-" + platform + "-" + hash;
    }

    private String searchUrl(String keyword, String platform) {
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        return switch (platform) {
            case PROVIDER_JD_ENTERPRISE -> "https://search.jd.com/Search?keyword=" + encoded;
            case PROVIDER_1688 -> "https://s.1688.com/selloffer/offer_search.htm?keywords=" + encoded;
            default -> "";
        };
    }

    private String platformLabel(String platform) {
        return switch (platform) {
            case PROVIDER_JD_ENTERPRISE -> "京东企业购";
            case PROVIDER_1688 -> "1688";
            default -> platform;
        };
    }

    private List<String> normalizePlatforms(List<String> platforms) {
        Set<String> normalized = new LinkedHashSet<>();
        List<String> source = platforms == null || platforms.isEmpty() ? DEFAULT_PLATFORMS : platforms;
        for (String platform : source) {
            String value = StringUtils.hasText(platform) ? platform.trim().toUpperCase(Locale.ROOT) : "";
            if ("JD".equals(value) || "JD_VOP".equals(value) || "JINGDONG".equals(value)
                || "京东".equals(platform) || "京东企业购".equals(platform)) {
                value = PROVIDER_JD_ENTERPRISE;
            } else if ("ALIBABA_1688".equals(value) || "1688".equals(value)) {
                value = PROVIDER_1688;
            }
            if (DEFAULT_PLATFORMS.contains(value)) {
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            normalized.addAll(DEFAULT_PLATFORMS);
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeCategory(String category) {
        if ("工程项目".equals(category) || "实物资产/无形资产/长摊等".equals(category) || "服务/费用/低值易耗等".equals(category)) {
            return category;
        }
        String value = StringUtils.hasText(category) ? category.trim().toUpperCase(Locale.ROOT) : "";
        if ("ENGINEERING".equals(value) || "PROJECT".equals(value)) {
            return "工程项目";
        }
        if ("CONSUMABLE".equals(value) || "LOW_VALUE_CONSUMABLE".equals(value)) {
            return "服务/费用/低值易耗等";
        }
        if ("ASSET".equals(value)) {
            return "实物资产/无形资产/长摊等";
        }
        if ("OTHER".equals(value) || "SERVICE".equals(value) || "FEE".equals(value)
            || "低值易耗".equals(category) || "服务".equals(category) || "费用".equals(category) || "其他".equals(category)) {
            return "服务/费用/低值易耗等";
        }
        if ("资产".equals(category) || "实物资产".equals(category) || "无形资产".equals(category) || "长摊".equals(category)) {
            return "实物资产/无形资产/长摊等";
        }
        return "服务/费用/低值易耗等";
    }

    private record MockProduct(
        String itemName,
        String specModel,
        BigDecimal unitPrice,
        String supplierName,
        String stockStatus,
        String deliveryLeadTime,
        BigDecimal score
    ) {
    }
}
