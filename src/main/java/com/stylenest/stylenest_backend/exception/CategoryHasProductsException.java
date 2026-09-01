package com.stylenest.stylenest_backend.exception;

public class CategoryHasProductsException extends RuntimeException {

    public CategoryHasProductsException(String message) {
        super(message);
    }
}
