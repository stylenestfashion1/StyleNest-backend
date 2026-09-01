package com.stylenest.stylenest_backend.exception;

/**
 * Thrown when the customer's cart no longer matches an already-reserved,
 * not-yet-paid online order (e.g. they changed the cart after clicking Pay).
 * The existing pending order must be finished or cancelled via
 * PUT /api/orders/{id}/cancel before a new one can be started.
 */
public class PendingPaymentExistsException extends RuntimeException {

    public PendingPaymentExistsException(String message) {
        super(message);
    }
}
