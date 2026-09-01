package com.stylenest.stylenest_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.auth.AuthResponse;
import com.stylenest.stylenest_backend.dto.auth.LoginRequest;
import com.stylenest.stylenest_backend.dto.auth.RegisterRequest;
import com.stylenest.stylenest_backend.dto.verification.ForgotPasswordRequestDTO;
import com.stylenest.stylenest_backend.dto.verification.ResetPasswordRequestDTO;
import com.stylenest.stylenest_backend.dto.verification.VerifyOtpRequestDTO;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.AuthService;

 import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }
    
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request) {

        authService.forgotPassword(request);

        return ResponseEntity.ok(
        		ApiResponse.success("OTP sent successfully.", null));       
    }
    
    
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<String>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequestDTO request) {

        authService.verifyOtp(request);

        return ResponseEntity.ok(
                ApiResponse.success("OTP verified successfully.", null)
        );
    }
    
    
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request) {

        authService.resetPassword(request);

        return ResponseEntity.ok(
                ApiResponse.success("Password reset successfully.", null)
        );
    }
}