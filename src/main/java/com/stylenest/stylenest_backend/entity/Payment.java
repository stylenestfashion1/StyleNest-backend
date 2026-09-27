package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.enums.PaymentProvider;
import com.stylenest.stylenest_backend.enums.PaymentState;

import jakarta.persistence.*;
import lombok.*;

/**
 * At most one row per Order -- separate from Order.paymentStatus (which
 * stays the single coarse status every existing call site already reads/
 * writes). This is where the actual Razorpay identifiers and fine-grained
 * lifecycle live. A single Razorpay order itself legitimately accepts
 * multiple sequential payment attempts (e.g. a declined card followed by
 * a successful retry) until one succeeds, so a retry updates this same
 * row in place rather than inserting a second one -- see
 * PaymentRepository.findByOrder / PaymentServiceImpl.completeInitiate.
 *
 * amount/currency are captured here at creation time from the Order's own
 * server-resolved total -- never from anything the frontend supplied --
 * so this row is also the record of what was actually charged.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    // Razorpay's order_id (e.g. "order_xxx") -- the trusted key used to
    // resolve which internal Order a verify/webhook call is about. Never
    // resolved from a client-supplied internal order id.
    @Column(nullable = false, unique = true, length = 64)
    private String providerOrderId;

    // Razorpay's payment_id (e.g. "pay_xxx") -- null until the customer
    // actually attempts payment inside Razorpay Checkout.
    @Column(unique = true, length = 64)
    private String providerPaymentId;

    // Snapshot of Order.totalAmount/currency at the moment this payment
    // attempt was created against Razorpay -- the exact amount the
    // Razorpay Order was created for, kept here even if the Order itself
    // is (in principle) never supposed to change afterward.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentState status;

    // Razorpay's own error_description for a failed payment -- safe,
    // customer/admin-readable text, never a raw exception or secret.
    private String failureReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
