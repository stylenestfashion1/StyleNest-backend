package com.stylenest.stylenest_backend.dto.auth;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;

    private String message;

    private String fullName;

    private String email;

    private String role;
}
