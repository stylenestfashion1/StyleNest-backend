package com.stylenest.stylenest_backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @NotBlank(message = "Full Name is required")
    private String fullName;

    private String phone;
}