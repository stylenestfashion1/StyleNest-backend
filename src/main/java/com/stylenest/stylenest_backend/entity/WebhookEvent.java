package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

/**
 * Records every Cashfree webhook delivery id we've already processed,
 * purely so a duplicate delivery (Cashfree retries on any non-2xx
 * response or a slow reply, and the same event can legitimately arrive
 * more than once) is a no-op the second time -- see
 * PaymentServiceImpl.handleWebhook. The unique constraint on eventId is
 * the actual idempotency guarantee, not just a lookup convenience.
 */
@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cashfree's x-idempotency-header -- unique per webhook delivery attempt.
    @Column(nullable = false, unique = true, length = 64)
    private String eventId;

    // e.g. "payment.captured", "payment.failed" -- for admin/debugging visibility only.
    @Column(nullable = false, length = 64)
    private String eventType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime receivedAt;
}
