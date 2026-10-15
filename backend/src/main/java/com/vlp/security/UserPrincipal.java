package com.vlp.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private final Long id;
    private final String username;
    private final String role;

    /** Any role allowed into the admin portal (including read-only DATA_VIEWER). */
    public boolean isStaff() {
        return AdminRoles.isStaff(this);
    }

    /** @deprecated use {@link #isStaff()} or {@link #isSuperAdmin()} — DATA_VIEWER is not a writer. */
    @Deprecated
    public boolean isAdmin() {
        return isStaff();
    }

    public boolean isSuperAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean canWriteContent() {
        return AdminRoles.canWriteContent(this);
    }

    public boolean canManageUsers() {
        return AdminRoles.canManageUsers(this);
    }
}
