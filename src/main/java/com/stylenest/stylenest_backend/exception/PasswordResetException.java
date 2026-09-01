package com.stylenest.stylenest_backend.exception;

public class PasswordResetException extends RuntimeException{

	public PasswordResetException (String message) {
		super(message);
	}
}
