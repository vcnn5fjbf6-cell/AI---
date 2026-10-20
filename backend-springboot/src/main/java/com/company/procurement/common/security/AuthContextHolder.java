package com.company.procurement.common.security;

import com.company.procurement.common.exception.BizException;

public final class AuthContextHolder {

    private static final ThreadLocal<AuthUser> USER_HOLDER = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(AuthUser authUser) {
        USER_HOLDER.set(authUser);
    }

    public static AuthUser get() {
        return USER_HOLDER.get();
    }

    public static AuthUser requireUser() {
        AuthUser authUser = get();
        if (authUser == null) {
            throw new BizException("UNAUTHORIZED", "Not logged in or session expired");
        }
        return authUser;
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
