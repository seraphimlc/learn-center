package com.vlp.security;

import com.vlp.common.BusinessException;

/**
 * Admin portal role checks. Prefer these over ad-hoc string compares.
 * <ul>
 *   <li>ADMIN — full access (users, content, config)</li>
 *   <li>CONTENT_ADMIN — content CMS write; no user/role/password ops</li>
 *   <li>DATA_VIEWER — read-only dashboard / lists / export</li>
 * </ul>
 */
public final class AdminRoles {
    private AdminRoles() {}

    public static boolean isStaff(UserPrincipal p) {
        if (p == null) return false;
        String r = p.getRole();
        return "ADMIN".equals(r) || "CONTENT_ADMIN".equals(r) || "DATA_VIEWER".equals(r);
    }

    public static boolean canManageUsers(UserPrincipal p) {
        return p != null && "ADMIN".equals(p.getRole());
    }

    public static boolean canWriteContent(UserPrincipal p) {
        if (p == null) return false;
        String r = p.getRole();
        return "ADMIN".equals(r) || "CONTENT_ADMIN".equals(r);
    }

    public static boolean canWriteConfig(UserPrincipal p) {
        return p != null && "ADMIN".equals(p.getRole());
    }

    public static void requireUserManager(UserPrincipal p) {
        if (!canManageUsers(p)) {
            throw new BusinessException(403, "仅超级管理员可管理用户与角色");
        }
    }

    public static void requireContentWriter(UserPrincipal p) {
        if (!canWriteContent(p)) {
            throw new BusinessException(403, "当前角色无内容写权限（数据查看员只读）");
        }
    }

    public static void requireConfigWriter(UserPrincipal p) {
        if (!canWriteConfig(p)) {
            throw new BusinessException(403, "仅超级管理员可修改运营配置与用户导入");
        }
    }
}
