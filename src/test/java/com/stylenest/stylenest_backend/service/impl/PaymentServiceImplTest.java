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

import com.stylenest.stylenest_backend.dto.order.GuestOrderItemRequest;
import com.stylenest.stylenest_backend.dto.order.GuestShippingAddressRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentGuestInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.PaymentVerifyRequest;
import com.stylenest.stylenest_backend.dto.payment.PaymentVerifyResponse;
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
import com.stylenest.stylenest_backend.service.PaymentProviderClient.PaymentOutcome;
import com.stylenest.stylenest_backend.service.PaymentProviderClient.ProviderOrderStatus;
import com.stylenest.stylenest_backend.service.PaymentProviderClient.WebhookEventResult;

/**
 * Everything here is genuinely testable without a real Cashfree account:
 * PaymentProviderClient is mocked, so the actual Cashfree REST round-trip
 * and the real webhook-signature cryptography against a live secret are
 * NOT exercised here -- that lives in CashfreePaymentProviderClient itself
 * and in manual/E2E testing with Cashfree sandbox credentials, which this
 * environment doesn't have. What IS verified here is exactly the logic
 * this class is responsible for: resolving orders only by our own stored
 * providerOrderId (never a client-supplied internal id), always
 * re-confirming the real outcome server-to-server rather than trusting
 * the client, never cancelling an Order on a first failed payment
 * attempt, idempotency against repeat verify calls / duplicate webhooks,
 * and cart-clearing only on real success.
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
                paymentProviderClient);

        user = User.builder().id(1L).email("customer@example.com").fullName("Customer").build();

        order = Order.builder()
                .id(99L)
                .orderNumber("SN-1000-ABCDEF")
                .user(user)
                .shippingFullName("Customer")
                .shippingPhone("9999999999")
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
                .provider(PaymentProvider.CASHFREE)
                .providerOrderId(providerOrderId)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(status)
                .build();
    }

    // --- initiate / initiateForGuest ---

    @Test
    void initiate_firstCall_createsGatewayOrderUsingServerResolvedAmount() {

        when(orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE)).thenReturn(order);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.empty());
        when(paymentProviderClient.createOrder(
                org.mockito.ArgumentMatchers.eq(new BigDecimal("2799.00")),
                org.mockito.ArgumentMatchers.eq(Currency.INR),
                org.mockito.ArgumentMatchers.eq("SN-1000-ABCDEF"),
                any()))
                .thenReturn(new PaymentProviderClient.ProviderOrder("SN-1000-ABCDEF", "session_abc123"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentInitiateResponse response = paymentService.initiate();

        assertThat(response.getOrderId()).isEqualTo(99L);
        assertThat(response.getProviderOrderId()).isEqualTo("SN-1000-ABCDEF");
        assertThat(response.getPaymentSessionId()).isEqualTo("session_abc123");
        assertThat(response.getCurrency()).isEqualTo(Currency.INR);

        // The amount/currency sent to Cashfree came from the Order, never
        // from anything a client could have supplied to this no-arg call.
        verify(paymentProviderClient).createOrder(
                org.mockito.ArgumentMatchers.eq(new BigDecimal("2799.00")),
                org.mockito.ArgumentMatchers.eq(Currency.INR),
                org.mockito.ArgumentMatchers.eq("SN-1000-ABCDEF"),
                any());
    }

    @Test
    void initiate_secondCallForSameOrder_refreshesSessionWithoutCreatingAnotherGatewayOrder() {

        Payment existing = buildPayment("SN-1000-ABCDEF", PaymentState.CREATED);

        when(orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE)).thenReturn(order);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(existing));
        when(paymentProviderClient.refreshPaymentSession("SN-1000-ABCDEF")).thenReturn("session_refreshed");

        PaymentInitiateResponse response = paymentService.initiate();

        assertThat(response.getProviderOrderId()).isEqualTo("SN-1000-ABCDEF");
        assertThat(response.getPaymentSessionId()).isEqualTo("session_refreshed");
        verify(paymentProviderClient, never()).createOrder(any(), any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void initiateForGuest_buildsGuestOrderRequestWithOnlinePaymentMethodAndDelegates() {

        PaymentGuestInitiateRequest request = PaymentGuestInitiateRequest.builder()
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
                .shippingFullName("Guest Customer")
                .shippingPhone("9998887777")
                .totalAmount(new BigDecimal("500.00"))
                .currency(Currency.INR)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PENDING)
                .orderItems(new ArrayList<>())
                .build();

        when(orderService.reserveGuestOrderForOnlinePayment(any())).thenReturn(guestOrder);
        when(paymentRepository.findByOrder(guestOrder)).thenReturn(Optional.empty());
        when(paymentProviderClient.createOrder(
                org.mockito.ArgumentMatchers.eq(new BigDecimal("500.00")),
                org.mockito.ArgumentMatchers.eq(Currency.INR),
                org.mockito.ArgumentMatchers.eq("SN-GUEST-1"),
                any()))
                .thenReturn(new PaymentProviderClient.ProviderOrder("SN-GUEST-1", "session_guest1"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentInitiateResponse response = paymentService.initiateForGuest(request);

        assertThat(response.getOrderId()).isEqualTo(101L);
        assertThat(response.getProviderOrderId()).isEqualTo("SN-GUEST-1");
        assertThat(response.getPaymentSessionId()).isEqualTo("session_guest1");

        var captor = org.mockito.ArgumentCaptor.forClass(com.stylenest.stylenest_backend.dto.order.GuestOrderRequest.class);
        verify(orderService).reserveGuestOrderForOnlinePayment(captor.capture());
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.ONLINE);
        assertThat(captor.getValue().getGuestEmail()).isEqualTo("guest@example.com");
        assertThat(captor.getValue().getCurrency()).isEqualTo(Currency.INR);
    }

    // --- verifyPayment ---

    @Test
    void verifyPayment_unknownProviderOrderId_throwsNotFound() {

        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .providerOrderId("order_does_not_exist")
                .build();

        when(paymentRepository.findByProviderOrderIdForUpdate("order_does_not_exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verifyPayment(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyPayment_alreadyPaidOrder_isIdempotentNoOp() {

        order.setPaymentStatus(PaymentStatus.PAID);
        Payment payment = buildPayment("SN-1000-ABCDEF", PaymentState.CAPTURED);

        PaymentVerifyRequest request = PaymentVerifyRequest.builder().providerOrderId("SN-1000-ABCDEF").build();

        when(paymentRepository.findByProviderOrderIdForUpdate("SN-1000-ABCDEF")).thenReturn(Optional.of(payment));

        PaymentVerifyResponse response = paymentService.verifyPayment(request);

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentProviderClient, never()).fetchOrderStatus(any());
        verify(orderService, never()).markOnlinePaymentPaid(any());
    }

    @Test
    void verifyPayment_success_marksOrderPaidAndClearsRegisteredCustomersCart() {

        Payment payment = buildPayment("SN-1000-ABCDEF", PaymentState.CREATED);
        Cart cart = Cart.builder().id(5L).user(user).items(new ArrayList<>()).currency(Currency.INR).build();

        PaymentVerifyRequest request = PaymentVerifyRequest.builder().providerOrderId("SN-1000-ABCDEF").build();

        when(paymentRepository.findByProviderOrderIdForUpdate("SN-1000-ABCDEF")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.fetchOrderStatus("SN-1000-ABCDEF"))
                .thenReturn(new ProviderOrderStatus(PaymentOutcome.SUCCESS, "cf_pay_1", "SUCCESS", null));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));

        paymentService.verifyPayment(request);

        assertThat(payment.getStatus()).isEqualTo(PaymentState.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("cf_pay_1");
        verify(orderService).markOnlinePaymentPaid(order);
        verify(cartRepository).save(cart);
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getCurrency()).isNull();
    }

    @Test
    void verifyPayment_successGuestOrder_marksPaidWithoutTouchingAnyCart() {

        order.setUser(null);
        order.setGuestEmail("guest@example.com");
        Payment payment = buildPayment("SN-1000-ABCDEF", PaymentState.CREATED);

        PaymentVerifyRequest request = PaymentVerifyRequest.builder().providerOrderId("SN-1000-ABCDEF").build();

        when(paymentRepository.findByProviderOrderIdForUpdate("SN-1000-ABCDEF")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.fetchOrderStatus("SN-1000-ABCDEF"))
                .thenReturn(new ProviderOrderStatus(PaymentOutcome.SUCCESS, "cf_pay_1", "SUCCESS", null));

        paymentService.verifyPayment(request);

        verify(orderService).markOnlinePaymentPaid(order);
        verify(cartRepository, never()).findByUser(any());
    }

    @Test
    void verifyPayment_stillPending_leavesOrderUntouched() {

        // Customer is still inside the Cashfree Checkout modal, or dropped
        // off without completing anything yet -- no terminal outcome to
        // apply, order stays exactly as it was.
        Payment payment = buildPayment("SN-1000-ABCDEF", PaymentState.CREATED);

        PaymentVerifyRequest request = PaymentVerifyRequest.builder().providerOrderId("SN-1000-ABCDEF").build();

        when(paymentRepository.findByProviderOrderIdForUpdate("SN-1000-ABCDEF")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.fetchOrderStatus("SN-1000-ABCDEF"))
                .thenReturn(new ProviderOrderStatus(PaymentOutcome.PENDING, null, "NOT_ATTEMPTED", null));

        PaymentVerifyResponse response = paymentService.verifyPayment(request);

        assertThat(payment.getStatus()).isEqualTo(PaymentState.CREATED);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(orderService, never()).markOnlinePaymentPaid(any());
    }

    /**
     * Regression test for the exact behavior this class deliberately
     * preserves: a failed sub-attempt inside the Cashfree Checkout modal
     * must never cancel the Order or restore stock, since the customer
     * may still be retrying a different payment method in the same
     * session.
     */
    @Test
    void verifyPayment_failed_onlyUpdatesPaymentRow_neverCancelsOrder() {

        Payment payment = buildPayment("SN-1000-ABCDEF", PaymentState.CREATED);

        PaymentVerifyRequest request = PaymentVerifyRequest.builder().providerOrderId("SN-1000-ABCDEF").build();

        when(paymentRepository.findByProviderOrderIdForUpdate("SN-1000-ABCDEF")).thenReturn(Optional.of(payment));
        when(paymentProviderClient.fetchOrderStatus("SN-1000-ABCDEF"))
                .thenReturn(new ProviderOrderStatus(PaymentOutcome.FAILURE, "cf_pay_1", "FAILED", "Card declined by issuer"));

        PaymentVerifyResponse response = paymentService.verifyPayment(request);

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

        when(paymentProviderClient.verifyWebhookSignature("{}", "bad-sig", "123")).thenReturn(false);

        assertThatThrownBy(() -> paymentService.handleWebhook("{}", "bad-sig", "123", "idem_1"))
                .isInstanceOf(InvalidPaymentSignatureException.class);

        verify(webhookEventRepository, never()).existsByEventId(any());
        verify(paymentRepository, never()).findByProviderOrderIdForUpdate(any());
    }

    @Test
    void handleWebhook_duplicateIdempotencyKey_isIdempotentNoOp() {

        when(paymentProviderClient.verifyWebhookSignature(any(), any(), any())).thenReturn(true);
        when(paymentProviderClient.parseWebhookEvent("{}"))
                .thenReturn(new WebhookEventResult("SN-1000-ABCDEF", PaymentOutcome.SUCCESS, null, null));
        when(webhookEventRepository.existsByEventId("idem_1")).thenReturn(true);

        paymentService.handleWebhook("{}", "sig", "123", "idem_1");

        verify(webhookEventRepository, never()).save(any());
        verify(paymentRepository, never()).findByProviderOrderIdForUpdate(any());
    }

    @Test
    void handleWebhook_success_marksPaidClearsCartAndRecordsEvent() {

        Payment payment = buildPayment("SN-1000-ABCDEF", PaymentState.CREATED);
        Cart cart = Cart.builder().id(5L).user(user).items(new ArrayList<>()).currency(Currency.INR).build();
        String body = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig", "123")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("idem_1")).thenReturn(false);
        when(paymentProviderClient.parseWebhookEvent(body))
                .thenReturn(new WebhookEventResult("SN-1000-ABCDEF", PaymentOutcome.SUCCESS, "cf_pay_1", null));
        when(paymentRepository.findByProviderOrderIdForUpdate("SN-1000-ABCDEF")).thenReturn(Optional.of(payment));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));

        paymentService.handleWebhook(body, "sig", "123", "idem_1");

        assertThat(payment.getStatus()).isEqualTo(PaymentState.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("cf_pay_1");
        verify(orderService).markOnlinePaymentPaid(order);
        verify(cartRepository).save(cart);

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(
                com.stylenest.stylenest_backend.entity.WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo("idem_1");
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("SUCCESS");
    }

    /**
     * Same regression as verifyPayment_failed_...: a failure webhook can
     * legitimately fire before an eventual success webhook for the same
     * transaction (e.g. a UPI retry after a wrong PIN) -- the webhook path
     * must never cancel the Order either.
     */
    @Test
    void handleWebhook_failure_onlyUpdatesPaymentRow_neverCancelsOrder() {

        Payment payment = buildPayment("SN-1000-ABCDEF", PaymentState.CREATED);
        String body = "{\"type\":\"PAYMENT_FAILED_WEBHOOK\"}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig", "123")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("idem_1")).thenReturn(false);
        when(paymentProviderClient.parseWebhookEvent(body))
                .thenReturn(new WebhookEventResult("SN-1000-ABCDEF", PaymentOutcome.FAILURE, "cf_pay_1", "Insufficient funds"));
        when(paymentRepository.findByProviderOrderIdForUpdate("SN-1000-ABCDEF")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(body, "sig", "123", "idem_1");

        assertThat(payment.getStatus()).isEqualTo(PaymentState.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Insufficient funds");
        verify(orderService, never()).markOnlinePaymentFailed(any());
        verify(orderService, never()).markOnlinePaymentPaid(any());
    }

    @Test
    void handleWebhook_unknownProviderOrderId_isSafelyIgnored() {

        String body = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig", "123")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("idem_1")).thenReturn(false);
        when(paymentProviderClient.parseWebhookEvent(body))
                .thenReturn(new WebhookEventResult("order_not_ours", PaymentOutcome.SUCCESS, "cf_pay_1", null));
        when(paymentRepository.findByProviderOrderIdForUpdate("order_not_ours")).thenReturn(Optional.empty());

        paymentService.handleWebhook(body, "sig", "123", "idem_1");

        verify(orderService, never()).markOnlinePaymentPaid(any());
        verify(orderService, never()).markOnlinePaymentFailed(any());
    }

    @Test
    void handleWebhook_orderAlreadyPaid_isIdempotentAgainstSynchronousVerifyRace() {

        order.setPaymentStatus(PaymentStatus.PAID);
        Payment payment = buildPayment("SN-1000-ABCDEF", PaymentState.CAPTURED);
        String body = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig", "123")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("idem_1")).thenReturn(false);
        when(paymentProviderClient.parseWebhookEvent(body))
                .thenReturn(new WebhookEventResult("SN-1000-ABCDEF", PaymentOutcome.SUCCESS, "cf_pay_1", null));
        when(paymentRepository.findByProviderOrderIdForUpdate("SN-1000-ABCDEF")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(body, "sig", "123", "idem_1");

        verify(orderService, never()).markOnlinePaymentPaid(any());
        verify(cartRepository, never()).findByUser(any());
    }

    @Test
    void handleWebhook_irrelevantEventType_isIgnored() {

        String body = "{\"type\":\"SOME_OTHER_WEBHOOK\"}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig", "123")).thenReturn(true);
        when(paymentProviderClient.parseWebhookEvent(body))
                .thenReturn(new WebhookEventResult("SN-1000-ABCDEF", PaymentOutcome.IRRELEVANT, null, null));

        paymentService.handleWebhook(body, "sig", "123", "idem_1");

        verify(webhookEventRepository, never()).existsByEventId(any());
        verify(orderService, never()).markOnlinePaymentPaid(any());
        verify(orderService, never()).markOnlinePaymentFailed(any());
    }

    @Test
    void handleWebhook_noIdempotencyKey_stillProcessesButSkipsDedupRecord() {

        // Older webhook version without x-idempotency-header -- must still
        // work; the order's own paymentStatus==PAID check is the real
        // backstop against duplicates either way.
        Payment payment = buildPayment("SN-1000-ABCDEF", PaymentState.CREATED);
        String body = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}";

        when(paymentProviderClient.verifyWebhookSignature(body, "sig", "123")).thenReturn(true);
        when(paymentProviderClient.parseWebhookEvent(body))
                .thenReturn(new WebhookEventResult("SN-1000-ABCDEF", PaymentOutcome.SUCCESS, "cf_pay_1", null));
        when(paymentRepository.findByProviderOrderIdForUpdate("SN-1000-ABCDEF")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(body, "sig", "123", null);

        assertThat(payment.getStatus()).isEqualTo(PaymentState.CAPTURED);
        verify(orderService).markOnlinePaymentPaid(order);
        verify(webhookEventRepository, never()).existsByEventId(any());
        verify(webhookEventRepository, never()).save(any());
    }
}
