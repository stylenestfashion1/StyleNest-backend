package com.stylenest.stylenest_backend.dto.verification;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequestDTO(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email")
        String email

) {}