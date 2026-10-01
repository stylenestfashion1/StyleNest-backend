package com.stylenest.stylenest_backend.exception;

/**
 * Wraps any failure talking to the DTDC courier API (network error, non-2xx
 * response, unexpected payload, or DTDC reporting success=false for a
 * consignment). The message shown to the client is always a generic, safe
 * string -- never the raw DTDC response, which may contain internal details.
 */
public class DtdcApiException extends RuntimeException {

    public DtdcApiException(String message) {
        super(message);
    }
}
