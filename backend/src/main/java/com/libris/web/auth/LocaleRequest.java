package com.libris.web.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LocaleRequest(
        @NotBlank @Pattern(regexp = "zh-CN|en") String locale) {
}
