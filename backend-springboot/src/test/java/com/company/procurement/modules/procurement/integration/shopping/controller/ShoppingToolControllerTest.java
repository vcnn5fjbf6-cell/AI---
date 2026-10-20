package com.company.procurement.modules.procurement.integration.shopping.controller;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.procurement.integration.shopping.config.ShoppingToolProperties;
import com.company.procurement.modules.procurement.integration.shopping.dto.ShoppingSearchDTO;
import com.company.procurement.modules.procurement.integration.shopping.vo.ShoppingSearchResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShoppingToolControllerTest {

    @Test
    void searchRejectsRequestsWhenServerTokenIsNotConfigured() {
        ShoppingToolController controller = new ShoppingToolController(new ShoppingToolProperties());
        ShoppingSearchDTO dto = new ShoppingSearchDTO();
        dto.setKeyword("mouse");

        assertThatThrownBy(() -> controller.search(dto, new MockHttpServletRequest()))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("SHOPPING_TOOL_UNAUTHORIZED");
    }

    @Test
    void searchAcceptsConfiguredBearerToken() {
        ShoppingToolProperties properties = new ShoppingToolProperties();
        properties.setToken("expected-token");
        ShoppingToolController controller = new ShoppingToolController(properties);
        ShoppingSearchDTO dto = new ShoppingSearchDTO();
        dto.setKeyword("mouse");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expected-token");

        Result<ShoppingSearchResultVO> result = controller.search(dto, request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getProducts()).isNotEmpty();
    }
}
