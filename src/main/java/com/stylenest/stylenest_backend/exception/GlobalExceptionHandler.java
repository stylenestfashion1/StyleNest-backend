package com.stylenest.stylenest_backend.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.stylenest.stylenest_backend.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Resource Not Found

    @ExceptionHandler({
            ResourceNotFoundException.class,
            UserNotFoundException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(
            RuntimeException ex) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                null);
    }

    // Duplicate Resources

    @ExceptionHandler({
            DuplicateResourceException.class,
            CategoryAlreadyExistsException.class,
            VariantAlreadyExistsException.class,
            WishlistItemAlreadyExistsException.class,
            EmailAlreadyExistsException.class,
            ProductHasOrderHistoryException.class,
            AddressHasOrderHistoryException.class,
            PendingPaymentExistsException.class,
            CategoryHasProductsException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleDuplicate(
            RuntimeException ex) {

        return buildResponse(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                null);
    }

    // Bad Request

    @ExceptionHandler({
            BadRequestException.class,
            IllegalArgumentException.class,
            InsufficientStockException.class,
            InvalidOtpException.class,
            OtpExpiredException.class,
            OtpAlreadyVerifiedException.class,
            PasswordResetException.class,
            InvalidPaymentSignatureException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(
            RuntimeException ex) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                null);
    }

    // Uploaded File Too Large

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex) {

        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file exceeds the maximum allowed size.",
                null);
    }

    // Invalid Login Credentials (Custom)

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidCredentials(
            InvalidCredentialsException ex) {

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage(),
                null);
    }

    // Spring Security Bad Credentials (Fallback)

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(
            BadCredentialsException ex) {

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password.",
                null);
    }

    // OTP Cooldown / Request Limit

    @ExceptionHandler({
            OtpCooldownException.class,
            TooManyOtpRequestsException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleOtpSecurity(
            RuntimeException ex) {

        return buildResponse(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getMessage(),
                null);
    }

    // Forbidden

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorized(
            UnauthorizedAccessException ex) {

        return buildResponse(
                HttpStatus.FORBIDDEN,
                ex.getMessage(),
                null);
    }

    // Payment Gateway Failure (Easebuzz network/API error -- never leak the
    // raw gateway response, which may contain internal details)

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiResponse<Object>> handlePaymentGateway(
            PaymentGatewayException ex) {

        return buildResponse(
                HttpStatus.BAD_GATEWAY,
                ex.getMessage(),
                null);
    }

    // Email Sending Failure

    @ExceptionHandler(EmailSendFailedException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailFailure(
            EmailSendFailedException ex) {

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                null);
    }

    // Invoice PDF Generation Failure

    @ExceptionHandler(InvoiceGenerationException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvoiceGeneration(
            InvoiceGenerationException ex) {

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                null);
    }

    // Validation Errors

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed.",
                errors);
    }

    // No Route Matches The Request URL

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFound(
            NoHandlerFoundException ex) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "The requested endpoint does not exist.",
                null);
    }

    // Static Resource Not Found (e.g. a /uploads/** file that was deleted
    // or never existed) -- without this explicit handler, the generic
    // Exception.class catch-all below would turn what Spring already
    // knows is a 404 into a 500.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(
            NoResourceFoundException ex) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "The requested resource does not exist.",
                null);
    }

    // Route Exists But Not For This HTTP Method

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {

        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                ex.getMessage(),
                null);
    }

    // Unknown Exception

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(
            Exception ex) {

        ex.printStackTrace();

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again later.",
                null);
    }

    // Common Response Builder

    private ResponseEntity<ApiResponse<Object>> buildResponse(
            HttpStatus status,
            String message,
            Object data) {

        return ResponseEntity.status(status)
                .body(ApiResponse.builder()
                        .success(false)
                        .message(message)
                        .data(data)
                        .build());
    }
}