package com.stylenest.stylenest_backend.dto.user;

import java.time.LocalDateTime;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponse {

    private Long id;

    private String fullName;

    private String email;

    private String phone;

    private String role;

    private LocalDateTime createdAt;
}