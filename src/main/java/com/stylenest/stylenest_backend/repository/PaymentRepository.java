package com.stylenest.stylenest_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // The trusted lookup key for a verify/webhook call -- resolving by this
    // (never by a client-supplied internal order id) is what makes it safe
    // for verify/webhook to be public endpoints. See PaymentServiceImpl.
    Optional<Payment> findByProviderOrderId(String providerOrderId);

    // At most one Payment row ever exists per Order -- a Razorpay order
    // itself accepts multiple payment attempts (e.g. a declined card
    // followed by a successful retry) until one succeeds, so a retry
    // reuses and updates this same row rather than minting a new one.
    // See PaymentServiceImpl.completeInitiate.
    Optional<Payment> findByOrder(Order order);
}
