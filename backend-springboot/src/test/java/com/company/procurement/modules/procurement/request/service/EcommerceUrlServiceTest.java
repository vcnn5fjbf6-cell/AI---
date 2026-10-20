package com.company.procurement.modules.procurement.request.service;

import com.company.procurement.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EcommerceUrlServiceTest {

    private final EcommerceUrlService service = new EcommerceUrlService();

    @Test
    void convertsJdMobileProductUrlToDesktopUrl() {
        String normalized = service.normalize(
            "https://item.m.jd.com/product/100104072733.html?utm_source=share"
        );

        assertThat(normalized).isEqualTo("https://item.jd.com/100104072733.html");
    }

    @Test
    void extractsAndResolvesJdShareText() {
        EcommerceUrlService shortLinkService = new EcommerceUrlService(
            ignored -> URI.create("https://item.m.jd.com/product/100229461892.html?utm_source=share")
        );

        String normalized = shortLinkService.normalize(
            "【京东】桥架接地线 https://3.cn/2WtD-4AA?jkl=abc 点击链接直接打开"
        );

        assertThat(normalized).isEqualTo("https://item.jd.com/100229461892.html");
    }

    @Test
    void removesTmallTrackingParametersButKeepsSku() {
        String normalized = service.normalize(
            "https://detail.tmall.com/item.htm?ali_refid=x&id=123456789&skuId=987654321&utm_source=share"
        );

        assertThat(normalized).isEqualTo("https://detail.tmall.com/item.htm?id=123456789&skuId=987654321");
    }

    @Test
    void rejectsLoginPageInsteadOfTreatingItAsProductUrl() {
        assertThatThrownBy(() -> service.normalize("https://login.taobao.com/member/login.jhtml"))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ECOMMERCE_URL_INVALID");
    }

    @Test
    void keepsNormalProductUrlFromOtherPlatform() {
        String normalized = service.normalize("https://detail.1688.com/offer/123456789.html");

        assertThat(normalized).isEqualTo("https://detail.1688.com/offer/123456789.html");
    }

    @Test
    void keepsExtractedShortUrlWhenExternalResolutionIsUnavailable() {
        EcommerceUrlService unavailableResolverService = new EcommerceUrlService(ignored -> {
            throw new BizException("ECOMMERCE_SHORT_LINK_RESOLVE_FAILED", "network unavailable");
        });

        String normalized = unavailableResolverService.normalize("【京东】商品 https://3.cn/2WtD-4AA 点击打开");

        assertThat(normalized).isEqualTo("https://3.cn/2WtD-4AA");
    }
}
