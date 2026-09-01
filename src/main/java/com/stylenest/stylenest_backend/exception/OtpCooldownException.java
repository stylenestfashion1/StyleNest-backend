package com.stylenest.stylenest_backend.exception;

public class OtpCooldownException extends RuntimeException{

	public OtpCooldownException (String message) {
		super(message);
	}
}
