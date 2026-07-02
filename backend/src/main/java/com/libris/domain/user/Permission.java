package com.libris.domain.user;

import java.util.EnumSet;
import java.util.Set;

/** Fine-grained staff capabilities. SUPER_ADMIN implicitly holds all of them. */
public enum Permission {
    CIRCULATION,
    MANAGE_CATALOG,
    MANAGE_READERS,
    MANAGE_POLICIES,
    MANAGE_SUGGESTIONS,
    VIEW_LOGS,
    VIEW_STATS,
    MANAGE_STAFF;

    /** Default grant for a newly created librarian. */
    public static Set<Permission> librarianDefaults() {
        return EnumSet.of(CIRCULATION, MANAGE_CATALOG, MANAGE_READERS, MANAGE_SUGGESTIONS, VIEW_STATS);
    }

    public static Set<Permission> all() {
        return EnumSet.allOf(Permission.class);
    }
}
