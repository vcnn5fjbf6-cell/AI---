package com.company.procurement.modules.supplier.support;

import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;

import java.util.List;

public final class SupplierQuoteAccess {

    public static final String VIEW_ALL_SUBMISSIONS_PERMISSION = "supplier_quote:view-all";

    private SupplierQuoteAccess() {
    }

    public static boolean canViewAllSubmissions(AuthUser authUser) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        List<String> permissions = authUser == null ? null : authUser.getPermissionCodes();
        return permissions != null
            && (permissions.contains("*")
            || permissions.contains("*:*:*")
            || permissions.contains(VIEW_ALL_SUBMISSIONS_PERMISSION));
    }
}
