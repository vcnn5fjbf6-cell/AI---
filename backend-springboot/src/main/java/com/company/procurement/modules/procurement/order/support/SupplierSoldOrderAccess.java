package com.company.procurement.modules.procurement.order.support;

import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;

import java.util.Objects;

public final class SupplierSoldOrderAccess {

    public static final String MANAGE_ALL_PERMISSION = "procurement_order:supplier-manage-all";

    private SupplierSoldOrderAccess() {
    }

    public static boolean canManageAll(AuthUser authUser) {
        return DataScopeHelper.isSupplierUser(authUser)
            && DataScopeHelper.hasPermission(authUser, MANAGE_ALL_PERMISSION);
    }

    public static boolean canOperate(AuthUser authUser, ProcurementOrderVO order) {
        if (!DataScopeHelper.isSupplierUser(authUser) || order == null || order.getSupplierId() == null) {
            return false;
        }
        if (canManageAll(authUser)) {
            return true;
        }
        return authUser.getSupplierId() != null
            && Objects.equals(authUser.getSupplierId(), order.getSupplierId());
    }

    public static boolean canUploadOrderDocuments(AuthUser authUser, ProcurementOrderVO order) {
        if (authUser == null || order == null || order.getSupplierId() == null) {
            return false;
        }
        if (DataScopeHelper.isSuperAdmin(authUser)) {
            return true;
        }
        return canOperate(authUser, order);
    }
}
