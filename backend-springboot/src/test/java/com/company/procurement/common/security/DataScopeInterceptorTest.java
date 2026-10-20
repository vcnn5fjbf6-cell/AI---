package com.company.procurement.common.security;

import com.company.procurement.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DataScopeInterceptorTest {

    private final DataScopeInterceptor interceptor = new DataScopeInterceptor();
    private final HandlerMethod handler = mock(HandlerMethod.class);

    @AfterEach
    void clearAuthContext() {
        AuthContextHolder.clear();
    }

    @Test
    void unboundSupervisorCanFilterSupplierOrderArchive() {
        AuthContextHolder.set(AuthUser.builder()
            .userId(255L)
            .userType(DataScopeHelper.USER_TYPE_SUPPLIER_SUPERVISOR)
            .roleCodes(List.of(DataScopeHelper.USER_TYPE_SUPPLIER_SUPERVISOR))
            .permissionCodes(List.of("procurement_order:supplier-manage-all"))
            .build());
        MockHttpServletRequest request = request("/api/procurement/orders/my/groups/HZCG-000001/export", "2");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
    }

    @Test
    void ordinarySupplierCannotFilterAnotherSupplier() {
        AuthContextHolder.set(AuthUser.builder()
            .userId(77L)
            .userType(DataScopeHelper.USER_TYPE_SUPPLIER)
            .supplierId(14L)
            .roleCodes(List.of(DataScopeHelper.USER_TYPE_SUPPLIER))
            .build());
        MockHttpServletRequest request = request("/api/procurement/orders/my/groups/HZCG-000001/export", "2");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FORBIDDEN");
    }

    @Test
    void supervisorPermissionDoesNotBypassOtherSupplierModules() {
        AuthContextHolder.set(AuthUser.builder()
            .userId(255L)
            .userType(DataScopeHelper.USER_TYPE_SUPPLIER_SUPERVISOR)
            .roleCodes(List.of(DataScopeHelper.USER_TYPE_SUPPLIER_SUPERVISOR))
            .permissionCodes(List.of("procurement_order:supplier-manage-all"))
            .build());
        MockHttpServletRequest request = request("/api/suppliers/page", "2");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FORBIDDEN");
    }

    private MockHttpServletRequest request(String uri, String supplierId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addParameter("supplierId", supplierId);
        return request;
    }
}
