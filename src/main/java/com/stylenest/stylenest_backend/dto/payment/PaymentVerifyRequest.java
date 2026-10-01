package com.stylenest.stylenest_backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Sent by our own frontend right after the Cashfree Checkout call
 * resolves (success or otherwise) so the backend can independently
 * confirm the real outcome server-to-server -- unlike the previous
 * gateway, Cashfree's client-side callback hands back no signed proof to
 * verify locally, so providerOrderId is the only input needed: it's the
 * TRUSTED lookup key used to resolve which internal Order this is about
 * (via Payment.providerOrderId), and the actual outcome always comes from
 * PaymentProviderClient.fetchOrderStatus, never from anything the client
 * claims. Shared by both the registered and guest flows.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentVerifyRequest {

    @NotBlank(message = "providerOrderId is required")
    private String providerOrderId;
}
