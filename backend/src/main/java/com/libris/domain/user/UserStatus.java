package com.libris.domain.user;

/**
 * BLOCKED suspends circulation privileges but still allows sign-in (patrons
 * must be able to see and settle fines). DISABLED locks the account entirely
 * (used for departed staff).
 */
public enum UserStatus {
    ACTIVE,
    BLOCKED,
    DISABLED
}
