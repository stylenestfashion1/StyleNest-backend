package com.stylenest.stylenest_backend.enums;

// COD is our own cash-on-delivery flow. ONLINE means "paid through the
// active payment gateway" -- Cashfree's own Checkout UI is what actually
// lets the customer pick card/UPI/netbanking/wallet, so the backend never
// needs to know that sub-choice ahead of time (Easebuzz/Razorpay-era CARD/
// UPI/NETBANKING values are gone: nothing outside the old gateway-specific
// initiation code ever branched on them -- see PaymentServiceImpl/Payment).
public enum PaymentMethod {
    COD,
    ONLINE
}
