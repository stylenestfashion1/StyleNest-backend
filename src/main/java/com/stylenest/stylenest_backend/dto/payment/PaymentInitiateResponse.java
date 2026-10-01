package com.stylenest.stylenest_backend.dto.payment;

import java.math.BigDecimal;

import com.stylenest.stylenest_backend.enums.Currency;

import lombok.*;

/**
 * Only ever contains information that is safe to hand to the frontend.
 * paymentSessionId is exactly what the frontend's Cashfree Checkout call
 * needs (cashfree.checkout({paymentSessionId})) -- no public/secret key
 * of any kind is ever returned here, unlike the previous gateway, since
 * Cashfree's frontend SDK needs no key at all once a session id exists.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInitiateResponse {

    private Long orderId;

    private String orderNumber;

    // Our own order reference, echoed back -- also the gateway's order id
    // (see CashfreePaymentProviderClient.createOrder).
    private String providerOrderId;

    private String paymentSessionId;

    // Human-readable amount (e.g. 1499.00), for display only.
    private BigDecimal amount;

    private Currency currency;
}
