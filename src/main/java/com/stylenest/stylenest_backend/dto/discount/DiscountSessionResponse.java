package com.stylenest.stylenest_backend.dto.discount;

import java.time.LocalDateTime;

import lombok.*;

/** Short-lived, server-issued proof that the customer's browser presented the valid permanent QR token. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountSessionResponse {

    private String sessionToken;
    private LocalDateTime expiresAt;
}
