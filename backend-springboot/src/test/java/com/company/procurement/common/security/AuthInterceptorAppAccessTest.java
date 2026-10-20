package com.company.procurement.common.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthInterceptorAppAccessTest {

    private final AuthInterceptor interceptor = new AuthInterceptor(null, null, null);

    @Test
    void requiredAppCodeMapsBusinessPrefixes() throws Exception {
        assertThat(requiredAppCode("/api/workflow/purchase-requests/page")).isEqualTo("PROCUREMENT");
        assertThat(requiredAppCode("/api/procurement/orders/my/page")).isEqualTo("PROCUREMENT");
        assertThat(requiredAppCode("/api/supplier/quote-requests/page")).isEqualTo("PROCUREMENT");
        assertThat(requiredAppCode("/api/admin/platform/users/access")).isEqualTo("PLATFORM");
        assertThat(requiredAppCode("/api/admin/users/page")).isEqualTo("PLATFORM");
        assertThat(requiredAppCode("/api/maintenance/dashboard")).isEqualTo("MAINTENANCE");
        assertThat(requiredAppCode("/api/ehs/overview")).isEqualTo("EHS");
        assertThat(requiredAppCode("/api/outsourcing/orders")).isEqualTo("OUTSOURCING");
        assertThat(requiredAppCode("/api/admin/outsourcing/settings")).isEqualTo("OUTSOURCING");
    }

    @Test
    void workbenchBusinessAggregationRequiresAnyAppAccess() throws Exception {
        assertThat(requiredAppCode("/api/workbench/notices/manage")).isEqualTo("PLATFORM");
        assertThat(requiredAnyAppCodes("/api/workbench/requests/page"))
            .containsExactly("PROCUREMENT", "PLATFORM", "MAINTENANCE", "EHS", "OUTSOURCING");
        assertThat(requiredAnyAppCodes("/api/workbench/follows"))
            .containsExactly("PROCUREMENT", "PLATFORM", "MAINTENANCE", "EHS", "OUTSOURCING");
        assertThat(requiredAnyAppCodes("/api/workbench/users/options"))
            .containsExactly("PROCUREMENT", "PLATFORM", "MAINTENANCE", "EHS", "OUTSOURCING");
    }

    @Test
    void publicAndPermissionRequestEntrypointsStayOutsideAppGate() throws Exception {
        assertThat(requiredAppCode("/api/platform/apps")).isEmpty();
        assertThat(requiredAppCode("/api/platform/me/access")).isEmpty();
        assertThat(requiredAppCode("/api/platform/permission-requests")).isEmpty();
        assertThat(requiredAppCode("/api/workbench/notices")).isEmpty();
        assertThat(requiredAnyAppCodes("/api/workbench/notices")).isEmpty();
    }

    @Test
    void ehsPublicApiIsWhitelistedWithoutPlatformLogin() throws Exception {
        Method method = AuthInterceptor.class.getDeclaredMethod("isWhitePath", String.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(interceptor, "/api/public/ehs/departments")).isTrue();
        assertThat((boolean) method.invoke(interceptor, "/api/public/ehs/review/approval")).isTrue();
        assertThat((boolean) method.invoke(interceptor, "/api/ehs/permits/page")).isFalse();
    }

    private String requiredAppCode(String path) throws Exception {
        Method method = AuthInterceptor.class.getDeclaredMethod("requiredAppCode", String.class);
        method.setAccessible(true);
        return (String) method.invoke(interceptor, path);
    }

    @SuppressWarnings("unchecked")
    private List<String> requiredAnyAppCodes(String path) throws Exception {
        Method method = AuthInterceptor.class.getDeclaredMethod("requiredAnyAppCodes", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(interceptor, path);
    }
}
