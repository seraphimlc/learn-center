package com.vlp.security;

import com.vlp.common.BusinessException;

/**
 * 管理端 RBAC 统一入口（避免各处字符串散落比较）。
 * <ul>
 *   <li>ADMIN — 超管：用户/角色/配置/导入 + 内容写</li>
 *   <li>CONTENT_ADMIN — 内容写：新闻/课程/课时/Banner/公告；不可管用户与站点配置</li>
 *   <li>DATA_VIEWER — 只读：看板/列表/学习数据导出；禁止一切写接口</li>
 * </ul>
 * Spring Security 仅校验「能否进 /api/admin/**」；细粒度写权限必须走本类。
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

    /** 学习数据导出含 PII 学习轨迹，仅超管与数据员。 */
    public static boolean canExportData(UserPrincipal p) {
        if (p == null) return false;
        String r = p.getRole();
        return "ADMIN".equals(r) || "DATA_VIEWER".equals(r);
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

    public static void requireDataExporter(UserPrincipal p) {
        if (!canExportData(p)) {
            throw new BusinessException(403, "仅超级管理员或数据查看员可导出学习数据");
        }
    }
}
