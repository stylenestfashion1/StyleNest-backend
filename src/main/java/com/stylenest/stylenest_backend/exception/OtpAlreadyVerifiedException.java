package com.stylenest.stylenest_backend.exception;

public class OtpAlreadyVerifiedException extends RuntimeException{

	public OtpAlreadyVerifiedException(String message) {
		super(message);
	}
}
