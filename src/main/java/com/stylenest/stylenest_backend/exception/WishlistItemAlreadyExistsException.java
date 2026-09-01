package com.stylenest.stylenest_backend.exception;

public class WishlistItemAlreadyExistsException extends RuntimeException {

    public WishlistItemAlreadyExistsException(String message) {
        super(message);
    }
}