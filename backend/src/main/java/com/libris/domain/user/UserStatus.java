package com.libris.domain.user;

/**
 * Account-level circulation status. BLOCKED prevents borrowing, renewing and
 * placing holds, but never prevents signing in (patrons must still be able to
 * see and settle their fines).
 */
public enum UserStatus {
    ACTIVE,
    BLOCKED
}
