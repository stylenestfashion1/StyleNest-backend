package com.stylenest.stylenest_backend.exception;

/**
 * Wraps any failure talking to Easebuzz (network error, non-2xx response,
 * unexpected payload). The message shown to the client is always a generic,
 * safe string -- never the raw gateway response, which may contain internal
 * details.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }
}
