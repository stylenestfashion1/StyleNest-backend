package com.stylenest.stylenest_backend.exception;

// Thrown when an order/payment is attempted in a currency this system can't
// yet actually collect payment for (currently: USD, until the merchant's
// Cashfree account is confirmed activated for international payments --
// see OrderServiceImpl.onlineInternationalPaymentsEnabled). Thrown before
// any Order row is persisted and before Cashfree is contacted -- see
// OrderServiceImpl.reserveOrder. Browsing/cart/checkout preview in that
// currency are unaffected; this only blocks the final order-creation step.
public class UnsupportedPaymentCurrencyException extends RuntimeException {

    public UnsupportedPaymentCurrencyException(String message) {
        super(message);
    }
}
