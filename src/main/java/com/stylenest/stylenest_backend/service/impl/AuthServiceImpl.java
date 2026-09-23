package com.stylenest.stylenest_backend.service.impl;

 import java.util.Collections;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.auth.AuthResponse;
import com.stylenest.stylenest_backend.dto.auth.LoginRequest;
import com.stylenest.stylenest_backend.dto.auth.RegisterRequest;
import com.stylenest.stylenest_backend.dto.verification.ForgotPasswordRequestDTO;
import com.stylenest.stylenest_backend.dto.verification.ResetPasswordRequestDTO;
import com.stylenest.stylenest_backend.dto.verification.VerifyOtpRequestDTO;
import com.stylenest.stylenest_backend.entity.OtpVerification;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Role;
import com.stylenest.stylenest_backend.exception.EmailAlreadyExistsException;
import com.stylenest.stylenest_backend.exception.InvalidCredentialsException;
import com.stylenest.stylenest_backend.exception.InvalidOtpException;
import com.stylenest.stylenest_backend.exception.PasswordResetException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.exception.UserNotFoundException;
import com.stylenest.stylenest_backend.repository.OtpVerificationRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.security.JwtService;
import com.stylenest.stylenest_backend.service.AuthService;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.OtpService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
 
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final OtpVerificationRepository otpRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists.");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(user);

        // Best-effort: a Brevo outage must never fail registration, which
        // has already durably written the user above.
        try {
            emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        String token = generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .message("Registration Successful")
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {

        try {

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

        } catch (BadCredentialsException ex) {

            throw new InvalidCredentialsException(
                    "Please check your email or password."
            );
        }

        // In normal operation this lookup always succeeds -- authenticate()
        // above already throws for a nonexistent email, caught as
        // InvalidCredentialsException with the same generic message below.
        // This orElseThrow is a defensive fallback for that path, so it must
        // use the same generic message rather than "User not found." --
        // otherwise a future change to the authentication provider config
        // (e.g. hideUserNotFoundExceptions) could silently start leaking
        // which emails are registered.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidCredentialsException("Please check your email or password."));

        String token = generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .message("Login Successful")
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
    
    

    private String generateToken(User user) {

        UserDetails userDetails =
                new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        user.getPassword(),
                        Collections.singletonList(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + user.getRole().name()
                                )
                        )
                );

        return jwtService.generateToken(userDetails, user.getRole());
    }

    @Override
    public void forgotPassword(ForgotPasswordRequestDTO request) {

        otpService.sendOtp(request.email());

    }

    @Override
    public void verifyOtp(VerifyOtpRequestDTO request) {

        otpService.verifyOtp(
                request.email(),
                request.otp()
        );

    }
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO request) {

        OtpVerification otpVerification = otpRepository.findTopByEmailOrderByCreatedAtDesc(request.email())
        		.orElseThrow(() ->
                new InvalidOtpException("No active OTP found. Please request a new OTP."));

        if (!otpVerification.isVerified()) {
            throw new PasswordResetException("Please verify OTP first.");
        }
        
        
        

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));

        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPassword())) {

            throw new PasswordResetException(
                    "New password cannot be same as old password.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));

        userRepository.save(user);

        otpRepository.delete(otpVerification);
    }
    
    
   
}