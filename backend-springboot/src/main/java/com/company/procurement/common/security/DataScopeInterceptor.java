package com.company.procurement.common.security;

import com.company.procurement.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

@Component
public class DataScopeInterceptor implements HandlerInterceptor {

    private static final String SUPPLIER_ORDER_PATH = "/api/procurement/orders";
    private static final String MANAGE_ALL_SUPPLIER_ORDERS = "procurement_order:supplier-manage-all";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }

        String supplierIdText = request.getParameter("supplierId");
        if (StringUtils.hasText(supplierIdText) && DataScopeHelper.isSupplierUser(authUser)) {
            Long supplierId = parseLong(supplierIdText, "supplierId");
            if (!Objects.equals(supplierId, authUser.getSupplierId()) && !canQueryAllSupplierOrders(request, authUser)) {
                throw new BizException("FORBIDDEN", "Supplier account cannot query other suppliers");
            }
        }

        String regionIdText = request.getParameter("regionId");
        if (StringUtils.hasText(regionIdText) && DataScopeHelper.SCOPE_REGION.equals(DataScopeHelper.resolveDataScope(authUser))) {
            Long regionId = parseLong(regionIdText, "regionId");
            if (!Objects.equals(regionId, authUser.getRegionId())) {
                throw new BizException("FORBIDDEN", "Current account cannot access other regions");
            }
        }
        return true;
    }

    private boolean canQueryAllSupplierOrders(HttpServletRequest request, AuthUser authUser) {
        String requestUri = request.getRequestURI();
        return requestUri != null
            && (requestUri.equals(SUPPLIER_ORDER_PATH) || requestUri.startsWith(SUPPLIER_ORDER_PATH + "/"))
            && DataScopeHelper.hasPermission(authUser, MANAGE_ALL_SUPPLIER_ORDERS);
    }

    private Long parseLong(String value, String fieldName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BizException("VALIDATION_ERROR", fieldName + " format is invalid");
        }
    }
}
