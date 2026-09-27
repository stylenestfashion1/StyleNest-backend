package com.stylenest.stylenest_backend.enums;

// The gateway a Payment row was processed through. Only one active
// provider today (RAZORPAY), but kept as an enum rather than a hardcoded
// constant so a future provider never requires a schema/column change --
// see service.PaymentProviderClient for the abstraction this backs.
public enum PaymentProvider {
    RAZORPAY
}
