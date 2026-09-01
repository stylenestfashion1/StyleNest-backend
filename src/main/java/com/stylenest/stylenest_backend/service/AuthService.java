package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.auth.AuthResponse;
import com.stylenest.stylenest_backend.dto.auth.LoginRequest;
import com.stylenest.stylenest_backend.dto.auth.RegisterRequest;
import com.stylenest.stylenest_backend.dto.verification.ForgotPasswordRequestDTO;
import com.stylenest.stylenest_backend.dto.verification.ResetPasswordRequestDTO;
import com.stylenest.stylenest_backend.dto.verification.VerifyOtpRequestDTO;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void forgotPassword(ForgotPasswordRequestDTO request);

    void verifyOtp(VerifyOtpRequestDTO request);

    void resetPassword(ResetPasswordRequestDTO request);
    
}