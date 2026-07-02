package com.libris.web.auth;

import java.util.List;

public record MeResponse(
        Long id,
        String username,
        String displayName,
        String role,
        String status,
        String email,
        String preferredLocale,
        String readerType,
        List<String> permissions,
        boolean emailVerified,
        boolean mfaEnabled,
        int passkeyCount) {
}
