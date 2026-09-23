package com.stylenest.stylenest_backend.dto.bulk;

import java.time.LocalDateTime;

import lombok.*;

/**
 * Admin-only view of one of the 10 permanent token slots. The plaintext
 * `token` field is intentionally included here -- unlike the old 24-hour
 * system, admin needs to be able to read a code back out to a wholesale
 * customer at any time, not just once at creation. This DTO must never be
 * returned from any customer-facing/public endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAccessTokenSummaryResponse {

    private Long id;
    private String token;
    private String status; // ACTIVE | REVOKED (computed, never persisted)
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
    private String assignedCustomerName;
    private String assignedCustomerEmail;
    private String assignedCustomerPhone;
    private LocalDateTime firstUsedAt;
    private LocalDateTime lastUsedAt;
    private String createdByAdminEmail;
}
