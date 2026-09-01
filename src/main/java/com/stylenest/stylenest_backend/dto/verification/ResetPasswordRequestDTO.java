package com.stylenest.stylenest_backend.dto.verification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequestDTO(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email")
        String email,

        @NotBlank(message = "New Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,64}$",
                message = "Password must be 8-64 characters and include at least one uppercase letter, "
                        + "one lowercase letter, one number, and one special character.")
        String newPassword

) {}
