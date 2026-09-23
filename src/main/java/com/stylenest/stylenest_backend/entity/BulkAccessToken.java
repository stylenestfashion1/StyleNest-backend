package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * One of exactly 10 permanent, owner-managed wholesale access codes (format
 * "STNEST-XXXXXXX"). Unlike a typical login credential, these are stored in
 * plaintext -- there are only 10 of them, admin needs to be able to look a
 * code back up to read it out to a customer over the phone, and each one
 * still carries strong per-code entropy (SecureRandom, ~36^7 combinations)
 * plus rate-limiting on the validate endpoint, so plaintext storage here is
 * a deliberate, reasonable choice rather than an oversight. Never returned
 * through any customer-facing/public API -- only admin endpoints (already
 * gated by ROLE_ADMIN) can read the token field.
 *
 * No expiry of any kind: a slot is valid indefinitely once provisioned,
 * until an admin revokes, reactivates, unassigns, or reissues it. See
 * BulkTokenServiceImpl for the provisioning/assignment logic.
 */
@Entity
@Table(name = "bulk_access_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkAccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    private LocalDateTime revokedAt;

    // Set the first time this token is used to successfully COMPLETE a
    // bulk order (not merely to browse the catalog) -- see
    // BulkOrderServiceImpl. Every subsequent order attempt with this same
    // token must match all three (name is stored for admin display only;
    // email+phone, normalized, are what's actually compared), enforcing
    // "one customer per token" without needing a separate customer account.
    // Cleared by an explicit admin "unassign" action, or by "reissue"
    // (which replaces the token value itself).
    private String assignedCustomerName;
    private String assignedCustomerEmail;
    private String assignedCustomerPhone;

    private LocalDateTime firstUsedAt;
    private LocalDateTime lastUsedAt;

    // Nullable: tokens auto-provisioned at startup (see
    // BulkTokenProvisioningRunner) have no HTTP-authenticated admin behind
    // them, unlike a manually-triggered provision/reissue call.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id", nullable = true)
    private User createdByAdmin;

    public boolean isAssigned() {
        return assignedCustomerEmail != null;
    }
}
