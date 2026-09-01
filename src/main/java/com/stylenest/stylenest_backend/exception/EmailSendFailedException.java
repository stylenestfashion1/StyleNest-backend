package com.stylenest.stylenest_backend.exception;

public class EmailSendFailedException extends RuntimeException {

	 public EmailSendFailedException(String message) {
	        super(message);
	    }
}
