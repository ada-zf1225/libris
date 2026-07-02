package com.libris.web.auth;

public record MeResponse(
        Long id,
        String username,
        String displayName,
        String role,
        String status,
        String email,
        String preferredLocale,
        String readerType) {
}
