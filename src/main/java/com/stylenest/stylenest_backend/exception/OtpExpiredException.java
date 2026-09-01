package com.stylenest.stylenest_backend.exception;

public class OtpExpiredException extends RuntimeException{

	public OtpExpiredException(String message) {
		super(message);
	}
}
