package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayGuestInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.RazorpayVerifyRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayVerifyResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Payment;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.entity.WebhookEvent;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentProvider;
import com.stylenest.stylenest_backend.enums.PaymentState;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.exception.InvalidPaymentSignatureException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.repository.CartRepository;
import com.stylenest.stylenest_backend.repository.PaymentRepository;
import com.stylenest.stylenest_backend.repository.WebhookEventRepository;
import com.stylenest.stylenest_backend.service.OrderService;
import com.stylenest.stylenest_backend.service.PaymentProviderClient;
import com.stylenest.stylenest_backend.service.PaymentService;

import lombok.RequiredArgsConstructor;

/**
 * Razorpay is the single active payment gateway. Order reservation
 * (stock, address snapshot, USD gating, pending-order dedup) is entirely
 * OrderService's existing responsibility, unchanged by this class -- this
 * service only ever talks to Razorpay for an already-reserved Order.
 *
 * IMPORTANT design note on payment failure: unlike the old Easebuzz
 * integration (a full-page redirect, where a "failure" callback really
 * was the end of that checkout attempt), Razorpay Checkout is an in-page
 * modal that lets the customer retry a different card/UPI/bank within
 * the SAME session after a decline. A payment.failed event for one
 * attempt does NOT mean the customer has abandoned the order -- Razorpay
 * itself documents this exact case (e.g. a UPI retry after a wrong PIN).
 * So a failed attempt here only ever updates the Payment row; it never
 * cancels the Order or restores stock (that stays exclusively the job of
 * the existing, unrelated PUT /api/orders/{id}/cancel flow). This is a
 * deliberate behavior change from the old Easebuzz flow, not an oversight.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final OrderService orderService;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final CartRepository cartRepository;
    private final PaymentProviderClient paymentProviderClient;
    private final ObjectMapper objectMapper;

    @Override
    public RazorpayInitiateResponse initiate() {

        Order order = orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE);

        return completeInitiate(order);
    }

    @Override
    public RazorpayInitiateResponse initiateForGuest(RazorpayGuestInitiateRequest request) {

        GuestOrderRequest orderRequest = GuestOrderRequest.builder()
                .guestEmail(request.getGuestEmail())
                .shippingAddress(request.getShippingAddress())
                .paymentMethod(PaymentMethod.ONLINE)
                .items(request.getItems())
                .currency(request.getCurrency())
                .build();

        Order order = orderService.reserveGuestOrderForOnlinePayment(orderRequest);

        return completeInitiate(order);
    }

    /**
     * Shared by both the registered and guest initiate flows. At most one
     * Payment row (and one Razorpay order) ever exists per internal Order
     * -- a Razorpay order accepts multiple payment attempts until one
     * succeeds, so re-calling this against the same still-pending order
     * (a refreshed payment page, or a retry after a declined card) reuses
     * the existing row/Razorpay order rather than minting a new one.
     */
    private RazorpayInitiateResponse completeInitiate(Order order) {

        Payment payment = paymentRepository.findByOrder(order)
                .orElseGet(() -> {

                    PaymentProviderClient.ProviderOrder providerOrder = paymentProviderClient.createOrder(
                            order.getTotalAmount(), order.getCurrency(), order.getOrderNumber());

                    return paymentRepository.save(Payment.builder()
                            .order(order)
                            .provider(PaymentProvider.RAZORPAY)
                            .providerOrderId(providerOrder.id())
                            .amount(order.getTotalAmount())
                            .currency(order.getCurrency())
                            .status(PaymentState.CREATED)
                            .build());
                });

        return RazorpayInitiateResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .razorpayOrderId(payment.getProviderOrderId())
                .razorpayKeyId(paymentProviderClient.publicKeyId())
                .amount(order.getTotalAmount())
                .amountMinor(toMinorUnits(order.getTotalAmount()))
                .currency(order.getCurrency())
                .build();
    }

    @Override
    public RazorpayVerifyResponse verifyPayment(RazorpayVerifyRequest request) {

        // The trusted lookup: which internal Order this is about is resolved
        // from OUR OWN previously-stored providerOrderId, never from
        // anything else the client supplies alongside it.
        Payment payment = paymentRepository.findByProviderOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for this order."));

        Order order = payment.getOrder();

        // Idempotent: a second verify call (double-click, tab refresh, race
        // with the webhook) for an already-paid order is a no-op.
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return toVerifyResponse(order);
        }

        // Razorpay's own guidance: verify using the order_id already held
        // server-side (from payment.getProviderOrderId(), resolved above),
        // never the razorpay_order_id the client echoes back -- even though
        // the two are guaranteed equal here (findByProviderOrderId only
        // matched because they're byte-identical), this keeps that
        // invariant explicit rather than incidental.
        boolean signatureValid = paymentProviderClient.verifyPaymentSignature(
                payment.getProviderOrderId(), request.getRazorpayPaymentId(), request.getRazorpaySignature());

        if (!signatureValid) {
            throw new InvalidPaymentSignatureException("Payment could not be verified.");
        }

        // Never trust the signature/client alone for the actual outcome --
        // fetch the real payment state from Razorpay itself.
        PaymentProviderClient.ProviderPayment providerPayment =
                paymentProviderClient.fetchPayment(request.getRazorpayPaymentId());

        if (!payment.getProviderOrderId().equals(providerPayment.orderId())) {
            // The payment_id genuinely exists at Razorpay, but doesn't
            // belong to the order we resolved -- never trust the signature
            // for this pairing regardless of what it claimed.
            throw new InvalidPaymentSignatureException("Payment could not be verified.");
        }

        payment.setProviderPaymentId(providerPayment.id());
        applyProviderStatus(payment, order, providerPayment);

        return toVerifyResponse(order);
    }

    @Override
    public void handleWebhook(String rawBody, String signatureHeader, String eventId) {

        if (!paymentProviderClient.verifyWebhookSignature(rawBody, signatureHeader)) {
            throw new InvalidPaymentSignatureException("Webhook signature verification failed.");
        }

        // Razorpay retries a webhook delivery on any non-2xx response or a
        // slow reply, and the same event can legitimately arrive more than
        // once even without a retry -- this is the actual idempotency
        // guarantee (a unique constraint on eventId), not just a lookup.
        if (eventId != null && webhookEventRepository.existsByEventId(eventId)) {
            return;
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            return; // malformed body despite a valid signature -- nothing sane to process
        }

        String eventType = payload.path("event").asText("");

        if (eventId != null) {
            webhookEventRepository.save(WebhookEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType.isBlank() ? "unknown" : eventType)
                    .build());
        }

        JsonNode paymentEntity = payload.path("payload").path("payment").path("entity");
        String providerOrderId = paymentEntity.path("order_id").asText(null);

        if (providerOrderId == null) {
            return; // an event with no associated order -- not relevant to any Payment row we track
        }

        Payment payment = paymentRepository.findByProviderOrderId(providerOrderId).orElse(null);

        if (payment == null) {
            return; // not one of ours -- never throw on an event the webhook shouldn't have sent us
        }

        Order order = payment.getOrder();

        // Only the events actually subscribed to in the Razorpay Dashboard
        // reach here in practice -- see PHASE 11 in the final report for
        // why each one is used. Anything else is ignored rather than acted on.
        boolean isSuccess = "payment.captured".equals(eventType) || "order.paid".equals(eventType);
        boolean isFailure = "payment.failed".equals(eventType);

        if (!isSuccess && !isFailure) {
            return;
        }

        // Idempotent against the synchronous verify call already having
        // resolved this order -- whichever of (verify, webhook) arrives
        // first wins.
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return;
        }

        String providerPaymentId = paymentEntity.path("id").asText(null);
        if (providerPaymentId != null) {
            payment.setProviderPaymentId(providerPaymentId);
        }

        if (isFailure) {

            // See class-level note: a failed attempt never cancels the
            // Order or restores stock -- the customer may still be
            // retrying inside the same Razorpay Checkout session.
            payment.setStatus(PaymentState.FAILED);
            payment.setFailureReason(paymentEntity.path("error_description").asText(null));
            paymentRepository.save(payment);

        } else {

            payment.setStatus(PaymentState.CAPTURED);
            paymentRepository.save(payment);
            markPaidAndClearCart(order);
        }
    }

    /**
     * AUTHORIZED != CAPTURED: an authorized-but-uncaptured payment is not
     * yet money in the merchant's account, and Razorpay auto-refunds it if
     * it's never captured. Rather than assume the Razorpay Dashboard's
     * auto-capture setting is on, an authorized payment is explicitly
     * captured here for the exact server-known order amount.
     */
    private void applyProviderStatus(Payment payment, Order order, PaymentProviderClient.ProviderPayment providerPayment) {

        switch (providerPayment.status()) {

            case "captured" -> {
                payment.setStatus(PaymentState.CAPTURED);
                paymentRepository.save(payment);
                markPaidAndClearCart(order);
            }

            case "authorized" -> {
                PaymentProviderClient.ProviderPayment captured = paymentProviderClient.capturePayment(
                        providerPayment.id(), order.getTotalAmount(), order.getCurrency());
                boolean nowCaptured = "captured".equals(captured.status());
                payment.setStatus(nowCaptured ? PaymentState.CAPTURED : PaymentState.AUTHORIZED);
                paymentRepository.save(payment);
                if (nowCaptured) {
                    markPaidAndClearCart(order);
                }
            }

            case "failed" -> {
                // See class-level note: never cancels the Order.
                payment.setStatus(PaymentState.FAILED);
                payment.setFailureReason(providerPayment.errorReason());
                paymentRepository.save(payment);
            }

            default -> {
                // created/pending -- nothing resolved yet; the webhook will
                // eventually deliver a terminal event.
                paymentRepository.save(payment);
            }
        }
    }

    private void markPaidAndClearCart(Order order) {

        orderService.markOnlinePaymentPaid(order);

        // Guest orders never had a server-side cart to begin with. A
        // registered customer's cart is cleared here (not inside
        // OrderService) so a stray repeat "Pay" click after success finds
        // an empty cart and fails fast, instead of reserving stock again
        // for an order that's already paid.
        if (order.getUser() != null) {
            clearCart(order.getUser());
        }
    }

    private void clearCart(User user) {

        cartRepository.findByUser(user).ifPresent(cart -> {

            cart.getItems().clear();
            cart.setTotalPrice(BigDecimal.ZERO);
            cart.setCurrency(null);

            cartRepository.save(cart);
        });
    }

    private RazorpayVerifyResponse toVerifyResponse(Order order) {

        return RazorpayVerifyResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .isGuest(order.getUser() == null)
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .build();
    }

    /** Razorpay amounts are always the smallest currency subunit (paise for INR, cents for USD) -- both are 2 decimal places, never floating point. */
    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
