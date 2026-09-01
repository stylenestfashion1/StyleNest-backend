package com.stylenest.stylenest_backend.exception;

public class ProductHasOrderHistoryException extends RuntimeException {

    public ProductHasOrderHistoryException(String message) {
        super(message);
    }
}
