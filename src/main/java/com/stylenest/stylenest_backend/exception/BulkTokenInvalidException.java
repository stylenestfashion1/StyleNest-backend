package com.stylenest.stylenest_backend.exception;

/**
 * Covers "does not exist", "revoked", and "bound to a different customer" --
 * deliberately one exception/message for all three so a caller can never
 * distinguish which case applies (no info leakage about token existence).
 */
public class BulkTokenInvalidException extends RuntimeException {
    public BulkTokenInvalidException(String message) {
        super(message);
    }
}
