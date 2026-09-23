package com.stylenest.stylenest_backend.exception;

public class BulkAccessRateLimitedException extends RuntimeException {
    public BulkAccessRateLimitedException(String message) {
        super(message);
    }
}
