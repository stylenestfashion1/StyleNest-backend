package com.stylenest.stylenest_backend.exception;

/** Token exists but active == false (admin-revoked). */
public class BulkTokenRevokedException extends RuntimeException {
    public BulkTokenRevokedException(String message) {
        super(message);
    }
}
