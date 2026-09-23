package com.stylenest.stylenest_backend.exception;

public class MobileNumberAlreadyUsedException extends RuntimeException {

    public MobileNumberAlreadyUsedException(String message) {
        super(message);
    }
}
