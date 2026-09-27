package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylenest.stylenest_backend.dto.order.GuestOrderItemRequest;
import com.stylenest.stylenest_backend.dto.order.GuestShippingAddressRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayGuestInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.RazorpayVerifyRequest;
import com.stylenest.stylenest_backend.dto.payment.RazorpayVerifyResponse;
import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.Payment;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Currency;
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

/**
 * Everything here except one thing is genuinely testable without a real
 * Razorpay account: PaymentProviderClient is mocked, so the actual Razorpay
 * Checkout completion / real signature cryptography against a live secret
 * is NOT exercised here -- that lives in RazorpayPaymentProviderClient
 * itself (a thin SDK wrapper) and in manual/E2E testing with Razorpay Test
 * Mode credentials, which this environment doesn't have. What IS verified
 * here is exactly the logic this class is responsible for: never trusting
 * a signature/callback alone, resolving orders only by our own stored
 * providerOrderId (never a client-supplied internal id), never cancelling
 * an Order on a first failed payment attempt, idempotency against repeat
 * verify calls / duplicate webhooks, and cart-clearing only on real success.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private PaymentProviderClient paymentProviderClient;

    private PaymentServiceImpl paymentService;

    private User user;
    private Order order;

    @BeforeEach
    void setUp() {

        paymentService = new PaymentServiceImpl(
                orderService, paymentRepository, webhookEventRepository, cartRepository,
                paymentProviderClient, new ObjectMapper());

        user = User.builder().id(1L).email("customer@example.com").fullName("Customer").build();

        order = Order.builder()
                .id(99L)
                .orderNumber("SN-1000-ABCDEF")
                .user(user)
                .totalAmount(new BigDecimal("2799.00"))
                .currency(Currency.INR)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PENDING)
                .orderItems(new ArrayList<>())
                .build();
    }

    private Payment buildPayment(String providerOrderId, PaymentState status) {

        return Payment.builder()
                .id(500L)
                .order(order)
                .provider(PaymentProvider.RAZORPAY)
                .providerOrderId(providerOrderId)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(status)
                .build();
    }

    // --- initiate / initiateForGuest ---

    @Test
    void initiate_firstCall_createsRazorpayOrderUsingServerResolvedAmount() {

        when(orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE)).thenReturn(order);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.empty());
        when(paymentProviderClient.createOrder(new BigDecimal("2799.00"), Currency.INR, "SN-1000-ABCDEF"))
                .thenReturn(new PaymentProviderClient.ProviderOrder("order_abc123", "created"));
        when(paymentProviderClient.publicKeyId()).thenReturn("rzp_test_key");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        RazorpayInitiateResponse response = paymentService.initiate();

        assertThat(response.getOrderId()).isEqualTo(99L);
        assertThat(response.getRazorpayOrderId()).isEqualTo("order_abc123");
        assertThat(response.getRazorpayKeyId()).isEqualTo("rzp_test_key");
        assertThat(response.getAmountMinor()).isEqualTo(279900L);
        assertThat(response.getCurrency()).isEqualTo(Currency.INR);

        // The amount/currency sent to Razorpay came from the Order, never
        // from anything a client could have supplied to this no-arg call.
        verify(paymentProviderClient).createOrder(new BigDecimal("2799.00"), Currency.INR, "SN-1000-ABCDEF");
    }

    @Test
    void initiate_secondCallForSameOrder_reusesExistingRazorpayOrderWithoutCreatingAnother() {

        Payment existing = buildPayment("order_abc123", PaymentState.CREATED);

        when(orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE)).thenReturn(order);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(existing));
        when(paymentProviderClient.publicKeyId()).thenReturn("rzp_test_key");

        RazorpayInitiateResponse response = paymentService.initiate();

        assertThat(response.getRazorpayOrderId()).isEqualTo("order_abc123");
        verify(paymentProviderClient, never()).createOrder(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void initiateForGuest_buildsGuestOrderRequestWithOnlinePaymentMethodAndDelegates() {

        RazorpayGuestInitiateRequest request = RazorpayGuestInitiateRequest.builder()
                .guestEmail("guest@example.com")
                .shippingAddress(GuestShippingAddressRequest.builder()
                        .fullName("Guest Customer")
                        .phone("9998887777")
                        .addressLine1("1 Guest Rd")
                        .city("Guest City")
                        .state("GS")
                        .postalCode("111111")
                        .country("India")
                        .build())
                .items(java.util.List.of(GuestOrderItemRequest.builder().productVariantId(1L).quantity(1).build()))
                .currency(Currency.INR)
                .build();

        Order guestOrder = Order.builder()
                .id(101L)
                .orderNumber("SN-GUEST-1")
                .user(null)
                .guestEmail("guest@example.com")
                .totalAmount(new BigDecimal("500.00"))
                .currency(Currency.INR)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PENDING)
                .orderItems(new ArrayList<>())
                .build();

        when(orderService.reserveGuestOrderForOnlinePayment(any())).thenReturn(guestOrder);
        when(paymentRepository.findByOrder(guestOrder)).thenReturn(Optional.empty());
        when(paymentProviderClient.createOrder(new BigDecimal("500.00"), Currency.INR, "SN-GUEST-1"))
                .thenReturn(new PaymentProviderClient.ProviderOrder("order_guest1", "created"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        RazorpayInitiateResponse response = paymentService.initiateForGuest(request);

        assertThat(response.getOrderId()).isEqualTo(101L);
        assertThat(response.getRazorpayOrderId()).isEqualTo("order_guest1");

        var captor = org.mockito.ArgumentCaptor.forClass(com.stylenest.stylenest_backend.dto.order.GuestOrderRequest.class);
        verify(orderService).reserveGuestOrderForOnlinePayment(captor.capture());
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.ONLINE);
        assertThat(captor.getValue().getGuestEmail()).isEqualTo("guest@example.com");
        assertThat(captor.getValue().getCurrency()).isEqualTo(Currency.INR);
    }

    // --- verifyPayment ---

    @Test
    void verifyPayment_unknownRazorpayOrderId_throwsNotFound() {

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_does_not_exist")
                .razorpayPaymentId("pay_x")
                .razorpaySignature("sig_x")
                .build();

        when(paymentRepository.findByProviderOrderId("order_does_not_exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verifyPayment(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyPayment_alreadyPaidOrder_isIdempotentNoOp() {

        order.setPaymentStatus(PaymentStatus.PAID);
        Payment payment = buildPayment("order_abc123", PaymentState.CAPTURED);

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_abc123")
                .razorpayPaymentId("pay_x")
                .razorpaySignature("sig_x")
                .build();

        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));

        RazorpayVerifyResponse response = paymentService.verifyPayment(request);

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentProviderClient, never()).verifyPaymentSignature(any(), any(), any());
        verify(orderService, never()).markOnlinePaymentPaid(any());
    }

    @Test
    void verifyPayment_invalidSignature_throwsAndNeverMarksOrder() {

        Payment payment = buildPayment("order_abc123", PaymentState.CREATED);

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_abc123")
                .razorpayPaymentId("pay_x")
                .razorpaySignature("forged-signature")
                .build();

        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.verifyPaymentSignature("order_abc123", "pay_x", "forged-signature"))
                .thenReturn(false);

        assertThatThrownBy(() -> paymentService.verifyPayment(request))
                .isInstanceOf(InvalidPaymentSignatureException.class);

        verify(paymentProviderClient, never()).fetchPayment(any());
        verify(orderService, never()).markOnlinePaymentPaid(any());
    }

    @Test
    void verifyPayment_paymentIdBelongsToADifferentRazorpayOrder_isRejected() {

        // A genuinely valid signature and a real payment_id at Razorpay --
        // but that payment_id's own order_id doesn't match the order we
        // resolved. Must never be trusted just because the signature passed.
        Payment payment = buildPayment("order_abc123", PaymentState.CREATED);

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_abc123")
                .razorpayPaymentId("pay_from_another_order")
                .razorpaySignature("sig_x")
                .build();

        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.verifyPaymentSignature("order_abc123", "pay_from_another_order", "sig_x"))
                .thenReturn(true);
        when(paymentProviderClient.fetchPayment("pay_from_another_order"))
                .thenReturn(new PaymentProviderClient.ProviderPayment(
                        "pay_from_another_order", "order_someone_elses", "captured", "card", null));

        assertThatThrownBy(() -> paymentService.verifyPayment(request))
                .isInstanceOf(InvalidPaymentSignatureException.class);

        verify(orderService, never()).markOnlinePaymentPaid(any());
    }

    @Test
    void verifyPayment_captured_marksOrderPaidAndClearsRegisteredCustomersCart() {

        Payment payment = buildPayment("order_abc123", PaymentState.CREATED);
        Cart cart = Cart.builder().id(5L).user(user).items(new ArrayList<>()).currency(Currency.INR).build();

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_abc123")
                .razorpayPaymentId("pay_x")
                .razorpaySignature("sig_x")
                .build();

        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.verifyPaymentSignature("order_abc123", "pay_x", "sig_x")).thenReturn(true);
        when(paymentProviderClient.fetchPayment("pay_x"))
                .thenReturn(new PaymentProviderClient.ProviderPayment("pay_x", "order_abc123", "captured", "card", null));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));

        paymentService.verifyPayment(request);

        assertThat(payment.getStatus()).isEqualTo(PaymentState.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_x");
        verify(orderService).markOnlinePaymentPaid(order);
        verify(cartRepository).save(cart);
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getCurrency()).isNull();
    }

    @Test
    void verifyPayment_capturedGuestOrder_marksPaidWithoutTouchingAnyCart() {

        order.setUser(null);
        order.setGuestEmail("guest@example.com");
        Payment payment = buildPayment("order_abc123", PaymentState.CREATED);

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_abc123")
                .razorpayPaymentId("pay_x")
                .razorpaySignature("sig_x")
                .build();

        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.verifyPaymentSignature("order_abc123", "pay_x", "sig_x")).thenReturn(true);
        when(paymentProviderClient.fetchPayment("pay_x"))
                .thenReturn(new PaymentProviderClient.ProviderPayment("pay_x", "order_abc123", "captured", "card", null));

        paymentService.verifyPayment(request);

        verify(orderService).markOnlinePaymentPaid(order);
        verify(cartRepository, never()).findByUser(any());
    }

    @Test
    void verifyPayment_authorizedThenCaptured_explicitlyCapturesAndMarksPaid() {

        Payment payment = buildPayment("order_abc123", PaymentState.CREATED);

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_abc123")
                .razorpayPaymentId("pay_x")
                .razorpaySignature("sig_x")
                .build();

        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.verifyPaymentSignature("order_abc123", "pay_x", "sig_x")).thenReturn(true);
        when(paymentProviderClient.fetchPayment("pay_x"))
                .thenReturn(new PaymentProviderClient.ProviderPayment("pay_x", "order_abc123", "authorized", "card", null));
        when(paymentProviderClient.capturePayment("pay_x", order.getTotalAmount(), order.getCurrency()))
                .thenReturn(new PaymentProviderClient.ProviderPayment("pay_x", "order_abc123", "captured", "card", null));
        when(cartRepository.findByUser(user)).thenReturn(Optional.empty());

        paymentService.verifyPayment(request);

        assertThat(payment.getStatus()).isEqualTo(PaymentState.CAPTURED);
        verify(paymentProviderClient).capturePayment("pay_x", new BigDecimal("2799.00"), Currency.INR);
        verify(orderService).markOnlinePaymentPaid(order);
    }

    @Test
    void verifyPayment_authorizedButCaptureFails_staysAuthorizedAndNeverMarksPaid() {

        Payment payment = buildPayment("order_abc123", PaymentState.CREATED);

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_abc123")
                .razorpayPaymentId("pay_x")
                .razorpaySignature("sig_x")
                .build();

        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.verifyPaymentSignature("order_abc123", "pay_x", "sig_x")).thenReturn(true);
        when(paymentProviderClient.fetchPayment("pay_x"))
                .thenReturn(new PaymentProviderClient.ProviderPayment("pay_x", "order_abc123", "authorized", "card", null));
        when(paymentProviderClient.capturePayment("pay_x", order.getTotalAmount(), order.getCurrency()))
                .thenReturn(new PaymentProviderClient.ProviderPayment("pay_x", "order_abc123", "authorized", "card", null));

        paymentService.verifyPayment(request);

        assertThat(payment.getStatus()).isEqualTo(PaymentState.AUTHORIZED);
        verify(orderService, never()).markOnlinePaymentPaid(any());
    }

    /**
     * Regression test for the exact bug caught during implementation: a
     * failed sub-attempt inside the Razorpay Checkout modal must never
     * cancel the Order or restore stock, since the customer may still be
     * retrying a different payment method in the same session.
     */
    @Test
    void verifyPayment_failed_onlyUpdatesPaymentRow_neverCancelsOrder() {

        Payment payment = buildPayment("order_abc123", PaymentState.CREATED);

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_abc123")
                .razorpayPaymentId("pay_x")
                .razorpaySignature("sig_x")
                .build();

        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.verifyPaymentSignature("order_abc123", "pay_x", "sig_x")).thenReturn(true);
        when(paymentProviderClient.fetchPayment("pay_x"))
                .thenReturn(new PaymentProviderClient.ProviderPayment(
                        "pay_x", "order_abc123", "failed", "card", "Card declined by issuer"));

        RazorpayVerifyResponse response = paymentService.verifyPayment(request);

        assertThat(payment.getStatus()).isEqualTo(PaymentState.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Card declined by issuer");
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING); // Order untouched
        verify(orderService, never()).markOnlinePaymentFailed(any());
        verify(orderService, never()).markOnlinePaymentPaid(any());
        verify(cartRepository, never()).findByUser(any());
    }

    // --- handleWebhook ---

    @Test
    void handleWebhook_invalidSignature_throwsAndProcessesNothing() {

        when(paymentProviderClient.verifyWebhookSignature("{}", "bad-sig")).thenReturn(false);

        assertThatThrownBy(() -> paymentService.handleWebhook("{}", "bad-sig", "evt_1"))
                .isInstanceOf(InvalidPaymentSignatureException.class);

        verify(webhookEventRepository, never()).existsByEventId(any());
        verify(paymentRepository, never()).findByProviderOrderId(any());
    }

    @Test
    void handleWebhook_duplicateEventId_isIdempotentNoOp() {

        when(paymentProviderClient.verifyWebhookSignature(any(), any())).thenReturn(true);
        when(webhookEventRepository.existsByEventId("evt_1")).thenReturn(true);

        paymentService.handleWebhook("{\"event\":\"payment.captured\"}", "sig", "evt_1");

        verify(webhookEventRepository, never()).save(any());
        verify(paymentRepository, never()).findByProviderOrderId(any());
    }

    @Test
    void handleWebhook_captured_marksPaidClearsCartAndRecordsEvent() {

        Payment payment = buildPayment("order_abc123", PaymentState.CREATED);
        Cart cart = Cart.builder().id(5L).user(user).items(new ArrayList<>()).currency(Currency.INR).build();

        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_x\",\"order_id\":\"order_abc123\",\"status\":\"captured\"}}}}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("evt_1")).thenReturn(false);
        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));

        paymentService.handleWebhook(body, "sig", "evt_1");

        assertThat(payment.getStatus()).isEqualTo(PaymentState.CAPTURED);
        verify(orderService).markOnlinePaymentPaid(order);
        verify(cartRepository).save(cart);

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(
                com.stylenest.stylenest_backend.entity.WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo("evt_1");
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("payment.captured");
    }

    /**
     * Same regression as verifyPayment_failed_...: payment.failed can
     * legitimately fire before an eventual payment.captured for the same
     * transaction (e.g. a UPI retry after a wrong PIN) -- the webhook path
     * must never cancel the Order either.
     */
    @Test
    void handleWebhook_paymentFailed_onlyUpdatesPaymentRow_neverCancelsOrder() {

        Payment payment = buildPayment("order_abc123", PaymentState.CREATED);

        String body = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_x\",\"order_id\":\"order_abc123\",\"error_description\":\"Insufficient funds\"}}}}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("evt_1")).thenReturn(false);
        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(body, "sig", "evt_1");

        assertThat(payment.getStatus()).isEqualTo(PaymentState.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Insufficient funds");
        verify(orderService, never()).markOnlinePaymentFailed(any());
        verify(orderService, never()).markOnlinePaymentPaid(any());
    }

    @Test
    void handleWebhook_unknownProviderOrderId_isSafelyIgnored() {

        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_x\",\"order_id\":\"order_not_ours\",\"status\":\"captured\"}}}}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("evt_1")).thenReturn(false);
        when(paymentRepository.findByProviderOrderId("order_not_ours")).thenReturn(Optional.empty());

        paymentService.handleWebhook(body, "sig", "evt_1");

        verify(orderService, never()).markOnlinePaymentPaid(any());
        verify(orderService, never()).markOnlinePaymentFailed(any());
    }

    @Test
    void handleWebhook_orderAlreadyPaid_isIdempotentAgainstSynchronousVerifyRace() {

        order.setPaymentStatus(PaymentStatus.PAID);
        Payment payment = buildPayment("order_abc123", PaymentState.CAPTURED);

        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_x\",\"order_id\":\"order_abc123\",\"status\":\"captured\"}}}}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("evt_1")).thenReturn(false);
        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(body, "sig", "evt_1");

        verify(orderService, never()).markOnlinePaymentPaid(any());
        verify(cartRepository, never()).findByUser(any());
    }

    @Test
    void handleWebhook_irrelevantEventType_isIgnored() {

        String body = "{\"event\":\"order.created\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_x\",\"order_id\":\"order_abc123\"}}}}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("evt_1")).thenReturn(false);
        when(paymentRepository.findByProviderOrderId("order_abc123"))
                .thenReturn(Optional.of(buildPayment("order_abc123", PaymentState.CREATED)));

        paymentService.handleWebhook(body, "sig", "evt_1");

        verify(orderService, never()).markOnlinePaymentPaid(any());
        verify(orderService, never()).markOnlinePaymentFailed(any());
    }
}
