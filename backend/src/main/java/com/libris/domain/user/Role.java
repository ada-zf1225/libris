package com.libris.domain.user;

public enum Role {
    SUPER_ADMIN,
    LIBRARIAN,
    READER;

    public boolean isStaff() {
        return this != READER;
    }
}
