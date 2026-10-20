package com.company.procurement.modules.supplier.service;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.supplier.entity.Supplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierProductLinkServiceTest {

    private final SupplierProductLinkService service = new SupplierProductLinkService(new EcommerceUrlService());

    @Test
    void convertsXfsInternalSkuToProductUrl() {
        String normalized = service.normalize("23629538", supplier("鑫方盛数智科技股份有限公司"));

        assertThat(normalized).isEqualTo("https://vip.xfs.com/vip/productsku/23629538.html");
    }

    @Test
    void exposesXfsRuleBySupplierIdentity() {
        assertThat(service.ruleCode(supplier("鑫方盛")))
            .isEqualTo(SupplierProductLinkService.RULE_XFS_INTERNAL_SKU);
        assertThat(service.ruleCode(supplier("其他供应商"))).isNull();
    }

    @Test
    void rejectsInternalSkuForUnconfiguredSupplier() {
        assertThatThrownBy(() -> service.normalize("23629538", supplier("其他供应商")))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("未配置内部商品编号规则");
    }

    @Test
    void keepsNormalProductUrlForAnySupplier() {
        String normalized = service.normalize(
            "https://item.m.jd.com/product/100104072733.html?utm_source=share",
            supplier("其他供应商")
        );

        assertThat(normalized).isEqualTo("https://item.jd.com/100104072733.html");
    }

    private Supplier supplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setSupplierName(name);
        return supplier;
    }
}
