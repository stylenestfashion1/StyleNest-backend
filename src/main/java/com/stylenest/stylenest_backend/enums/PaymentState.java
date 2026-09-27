package com.stylenest.stylenest_backend.enums;

// Fine-grained, provider-level payment lifecycle -- distinct from
// Order.paymentStatus (PENDING/PAID/FAILED/REFUNDED), which stays the
// single coarse status every existing order/invoice/email/admin call site
// already reads. This enum exists only on the Payment entity, to record
// exactly where a given gateway attempt is in Razorpay's own lifecycle
// without having to rework every existing Order.paymentStatus consumer.
//
// Maps directly onto Razorpay's own payment.status values (created ->
// authorized -> captured, or -> failed; refunded/partially_refunded come
// from the refund APIs/webhooks). AUTHORIZED is NOT the same as CAPTURED:
// an authorized-but-uncaptured payment is not yet money in the merchant's
// account and Razorpay auto-refunds it if it's never captured (see
// PaymentServiceImpl, which explicitly captures rather than assuming the
// Razorpay Dashboard's auto-capture setting).
public enum PaymentState {
    CREATED,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
