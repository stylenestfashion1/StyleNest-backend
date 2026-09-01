package com.stylenest.stylenest_backend.dto.payment;

import java.math.BigDecimal;

import com.stylenest.stylenest_backend.enums.PaymentMethod;

import lombok.*;

/**
 * Only ever contains information that is safe to hand to the frontend.
 * The Easebuzz key/salt and the raw access_key's originating request never
 * leave the backend beyond what's included here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EasebuzzInitiateResponse {

    private Long orderId;

    private String orderNumber;

    private BigDecimal amount;

    private PaymentMethod paymentMethod;

    // CARD: browser should navigate to this URL (Easebuzz-hosted checkout page).
    private String redirectUrl;

    // NETBANKING (seamless): raw HTML bank/3DS page -- must be rendered
    // (e.g. written into an iframe) so the customer can authenticate.
    private String bankRedirectHtml;

    // UPI (seamless collect request): human-readable status message, e.g.
    // "Approve the payment request in your UPI app."
    private String message;
}
