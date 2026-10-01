package com.stylenest.stylenest_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // The trusted lookup key for a verify/webhook call -- resolving by this
    // (never by a client-supplied internal order id) is what makes it safe
    // for verify/webhook to be public endpoints. See PaymentServiceImpl.
    Optional<Payment> findByProviderOrderId(String providerOrderId);

    // Same lookup, but row-locked for the duration of the caller's
    // transaction -- used by PaymentServiceImpl.verifyPayment and
    // handleWebhook, which can legitimately race each other for the same
    // order (the frontend calls verify right after Cashfree Checkout
    // resolves, at almost the same moment Cashfree's own webhook fires
    // server-to-server). Without this lock, two concurrent transactions
    // can both read paymentStatus != PAID before either commits, and both
    // apply the paid transition -- which duplicates non-transactional side
    // effects (the confirmation email, shipment creation) that a DB
    // rollback can't undo. Whichever call loses the race blocks here until
    // the winner commits, then re-reads the now-PAID row and no-ops via
    // the existing idempotency checks.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerOrderId = :providerOrderId")
    Optional<Payment> findByProviderOrderIdForUpdate(@Param("providerOrderId") String providerOrderId);

    // At most one Payment row ever exists per Order -- a Cashfree order
    // itself accepts multiple payment attempts (e.g. a declined card
    // followed by a successful retry) until one succeeds, so a retry
    // reuses and updates this same row rather than minting a new one.
    // See PaymentServiceImpl.completeInitiate.
    Optional<Payment> findByOrder(Order order);
}
