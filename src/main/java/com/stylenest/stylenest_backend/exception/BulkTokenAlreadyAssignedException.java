package com.stylenest.stylenest_backend.exception;

/**
 * Thrown at bulk order placement when the token is already bound to a
 * different customer's email+phone. Only relevant at checkout time --
 * merely browsing the catalog never checks assignment (see
 * BulkTokenServiceImpl.resolveActiveToken).
 */
public class BulkTokenAlreadyAssignedException extends RuntimeException {
    public BulkTokenAlreadyAssignedException(String message) {
        super(message);
    }
}
