package com.stylenest.stylenest_backend.exception;

public class TooManyOtpRequestsException extends RuntimeException {

	public TooManyOtpRequestsException (String message) {
		super(message);
	}
}
