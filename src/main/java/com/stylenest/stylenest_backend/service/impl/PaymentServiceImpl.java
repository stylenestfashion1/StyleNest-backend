package com.stylenest.stylenest_backend.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentGuestInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.PaymentVerifyRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentVerifyResponse;
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
import com.stylenest.stylenest_backend.service.PaymentProviderClient.PaymentOutcome;
import com.stylenest.stylenest_backend.service.PaymentProviderClient.ProviderOrderStatus;
import com.stylenest.stylenest_backend.service.PaymentProviderClient.WebhookEventResult;
import com.stylenest.stylenest_backend.service.PaymentService;

import lombok.RequiredArgsConstructor;

/**
 * Cashfree is the single active payment gateway. Order reservation
 * (stock, address snapshot, USD gating, pending-order dedup) is entirely
 * OrderService's existing responsibility, unchanged by this class -- this
 * service only ever talks to Cashfree (via PaymentProviderClient) for an
 * already-reserved Order.
 *
 * IMPORTANT design note on payment failure: like the previous gateway's
 * in-page modal, Cashfree Checkout (opened in "_modal" mode by the
 * frontend) lets the customer retry a different card/UPI/bank within the
 * SAME session after a decline. A failed sub-attempt does NOT mean the
 * customer has abandoned the order -- so a failed attempt here only ever
 * updates the Payment row; it never cancels the Order or restores stock
 * (that stays exclusively the job of the existing, unrelated
 * PUT /api/orders/{id}/cancel flow).
 *
 * Unlike the previous gateway, Cashfree's client-side checkout callback
 * hands back no signed proof to verify locally -- verifyPayment's job is
 * simply "look up the real, current status now" via
 * PaymentProviderClient.fetchOrderStatus, which is exactly as strong a
 * guarantee (arguably stronger, since there's no client-supplied artifact
 * to validate at all -- the server-to-server fetch is the only source of
 * truth, full stop).
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

    @Override
    public PaymentInitiateResponse initiate() {

        Order order = orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE);

        return completeInitiate(order);
    }

    @Override
    public PaymentInitiateResponse initiateForGuest(PaymentGuestInitiateRequest request) {

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
     * Payment row (and one gateway order) ever exists per internal Order
     * -- a Cashfree order accepts multiple payment attempts until one
     * succeeds, so re-calling this against the same still-pending order
     * (a refreshed payment page, or a retry after a declined card) reuses
     * the existing row/gateway order rather than minting a new one.
     */
    private PaymentInitiateResponse completeInitiate(Order order) {

        Payment payment = paymentRepository.findByOrder(order).orElse(null);
        String paymentSessionId;

        if (payment == null) {

            // First attempt for this order -- create the gateway order.
            PaymentProviderClient.CustomerDetails customer = new PaymentProviderClient.CustomerDetails(
                    "cust-" + order.getId(),
                    order.getShippingFullName(),
                    order.getUser() != null ? order.getUser().getEmail() : order.getGuestEmail(),
                    order.getShippingPhone());

            PaymentProviderClient.ProviderOrder providerOrder = paymentProviderClient.createOrder(
                    order.getTotalAmount(), order.getCurrency(), order.getOrderNumber(), customer);

            payment = paymentRepository.save(Payment.builder()
                    .order(order)
                    .provider(PaymentProvider.CASHFREE)
                    .providerOrderId(providerOrder.providerOrderId())
                    .amount(order.getTotalAmount())
                    .currency(order.getCurrency())
                    .status(PaymentState.CREATED)
                    .build());

            paymentSessionId = providerOrder.paymentSessionId();

        } else {

            // A gateway order already exists for this Order (refreshed
            // payment page, retry after a declined card) -- calling
            // createOrder again for the SAME order_id fails at Cashfree
            // ("order_already_exists"), so the existing order's current,
            // still-valid session is fetched instead.
            paymentSessionId = paymentProviderClient.refreshPaymentSession(payment.getProviderOrderId());
        }

        return PaymentInitiateResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .providerOrderId(payment.getProviderOrderId())
                .paymentSessionId(paymentSessionId)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .build();
    }

    @Override
    public PaymentVerifyResponse verifyPayment(PaymentVerifyRequest request) {

        // The trusted lookup: which internal Order this is about is resolved
        // from OUR OWN previously-stored providerOrderId, never from
        // anything else the client supplies alongside it. Row-locked so a
        // webhook racing this same call for the same order serializes
        // instead of both applying the paid transition (see
        // PaymentRepository.findByProviderOrderIdForUpdate).
        Payment payment = paymentRepository.findByProviderOrderIdForUpdate(request.getProviderOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for this order."));

        Order order = payment.getOrder();

        // Idempotent: a second verify call (double-click, tab refresh, race
        // with the webhook) for an already-paid order is a no-op.
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return toVerifyResponse(order);
        }

        // Never trust the client alone for the actual outcome -- fetch the
        // real payment state from Cashfree itself.
        ProviderOrderStatus status = paymentProviderClient.fetchOrderStatus(payment.getProviderOrderId());

        applyProviderStatus(payment, order, status);

        return toVerifyResponse(order);
    }

    @Override
    public void handleWebhook(String rawBody, String signatureHeader, String timestampHeader, String idempotencyKey) {

        if (!paymentProviderClient.verifyWebhookSignature(rawBody, signatureHeader, timestampHeader)) {
            throw new InvalidPaymentSignatureException("Webhook signature verification failed.");
        }

        WebhookEventResult event = paymentProviderClient.parseWebhookEvent(rawBody);

        if (event.outcome() == PaymentOutcome.IRRELEVANT || event.providerOrderId() == null) {
            return; // not one of the event types we act on, or no associated order
        }

        // Cashfree retries a webhook delivery on any non-2xx response or a
        // slow reply, and the same event can legitimately arrive more than
        // once even without a retry -- x-idempotency-header (unique per
        // delivery) is the actual idempotency guarantee, via a unique
        // constraint, not just a lookup. Older webhook payloads without
        // this header simply skip the dedup record (never blocks
        // processing -- the paymentStatus==PAID check below is the real
        // backstop either way).
        if (idempotencyKey != null && webhookEventRepository.existsByEventId(idempotencyKey)) {
            return;
        }

        // Row-locked for the same reason as verifyPayment's lookup -- this
        // call can race the frontend's synchronous verify call for the
        // same order.
        Payment payment = paymentRepository.findByProviderOrderIdForUpdate(event.providerOrderId()).orElse(null);

        if (payment == null) {
            return; // not one of ours -- never throw on an event the webhook shouldn't have sent us
        }

        Order order = payment.getOrder();

        if (idempotencyKey != null) {
            webhookEventRepository.save(WebhookEvent.builder()
                    .eventId(idempotencyKey)
                    .eventType(event.outcome().name())
                    .build());
        }

        // Idempotent against the synchronous verify call already having
        // resolved this order -- whichever of (verify, webhook) arrives
        // first wins.
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return;
        }

        if (event.providerPaymentId() != null) {
            payment.setProviderPaymentId(event.providerPaymentId());
        }

        if (event.outcome() == PaymentOutcome.FAILURE) {

            // See class-level note: a failed attempt never cancels the
            // Order -- the customer may still be retrying inside the same
            // Cashfree Checkout session.
            payment.setStatus(PaymentState.FAILED);
            payment.setFailureReason(event.failureReason());
            paymentRepository.save(payment);

        } else if (event.outcome() == PaymentOutcome.SUCCESS) {

            payment.setStatus(PaymentState.CAPTURED);
            paymentRepository.save(payment);
            markPaidAndClearCart(order);
        }
    }

    /**
     * Cashfree has no separate authorize-then-capture step the way the
     * previous gateway did -- a SUCCESS outcome from fetchOrderStatus IS
     * already-settled money, applied directly.
     */
    private void applyProviderStatus(Payment payment, Order order, ProviderOrderStatus status) {

        if (status.providerPaymentId() != null) {
            payment.setProviderPaymentId(status.providerPaymentId());
        }

        switch (status.outcome()) {

            case SUCCESS -> {
                payment.setStatus(PaymentState.CAPTURED);
                paymentRepository.save(payment);
                markPaidAndClearCart(order);
            }

            case FAILURE -> {
                // See class-level note: never cancels the Order.
                payment.setStatus(PaymentState.FAILED);
                payment.setFailureReason(status.failureReason());
                paymentRepository.save(payment);
            }

            default -> {
                // pending/not-yet-attempted -- nothing resolved yet; the
                // webhook will eventually deliver a terminal event, or the
                // customer is still inside the Checkout modal.
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
            cart.setTotalPrice(java.math.BigDecimal.ZERO);
            cart.setCurrency(null);

            cartRepository.save(cart);
        });
    }

    private PaymentVerifyResponse toVerifyResponse(Order order) {

        return PaymentVerifyResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .isGuest(order.getUser() == null)
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .build();
    }
}
