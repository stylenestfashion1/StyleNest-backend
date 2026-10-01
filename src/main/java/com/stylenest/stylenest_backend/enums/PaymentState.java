package com.stylenest.stylenest_backend.enums;

// Fine-grained, provider-level payment lifecycle -- distinct from
// Order.paymentStatus (PENDING/PAID/FAILED/REFUNDED), which stays the
// single coarse status every existing order/invoice/email/admin call site
// already reads. This enum exists only on the Payment entity, to record
// exactly where a given gateway attempt is, without having to rework
// every existing Order.paymentStatus consumer.
//
// CREATED -> CAPTURED (success) or -> FAILED is the path Cashfree
// actually uses -- Cashfree has no separate authorize-then-capture step
// the way a previous gateway integration did, so a successful payment
// goes straight to CAPTURED (see PaymentServiceImpl). AUTHORIZED is kept
// in the schema for generality/possible future use, not something the
// active Cashfree integration sets. REFUNDED/PARTIALLY_REFUNDED are
// likewise schema-ready for a future refund feature, not yet wired to
// any code path.
public enum PaymentState {
    CREATED,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
