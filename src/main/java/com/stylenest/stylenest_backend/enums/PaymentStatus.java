package com.stylenest.stylenest_backend.enums;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,

    // Deprecated: legacy alias for PAID, previously set incorrectly when an
    // order's orderStatus was set to DELIVERED (order/payment status must
    // stay independent). No code path sets this anymore.
    @Deprecated SUCCESS
}