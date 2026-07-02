package com.libris.web.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for expected, user-facing errors. {@code messageKey} is resolved
 * against the i18n bundle with {@code args} in the caller's locale.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String messageKey;
    private final Object[] args;

    public ApiException(HttpStatus status, String messageKey, Object... args) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
        this.args = args;
    }

    public static ApiException notFound(String messageKey, Object... args) {
        return new ApiException(HttpStatus.NOT_FOUND, messageKey, args);
    }

    public static ApiException conflict(String messageKey, Object... args) {
        return new ApiException(HttpStatus.CONFLICT, messageKey, args);
    }

    public static ApiException badRequest(String messageKey, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, messageKey, args);
    }

    public static ApiException tooManyRequests(String messageKey, Object... args) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, messageKey, args);
    }
}
