package com.stylenest.stylenest_backend.dto.admin;

import lombok.*;

/**
 * The full permanent special-offer URL (including the secret QR token) plus
 * the current QR Display Range -- admin-only (ROLE_ADMIN), used solely to
 * render/print the shop's one QR poster. Never exposed through any
 * public/customer-facing endpoint. The url NEVER changes between calls; the
 * display range reflects whatever Admin -> Rewards -> Custom Discount has
 * most recently saved.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDiscountQrResponse {

    private String url;
    private Integer minDiscountPercentage;
    private Integer maxDiscountPercentage;
}
