package com.agrimarket.api.error;

import org.springframework.http.HttpStatus;

/** 429 with a {@code Retry-After} header (seconds), e.g. a locked account. */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String code, String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
