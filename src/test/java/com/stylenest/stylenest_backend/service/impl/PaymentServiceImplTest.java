package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.stylenest.stylenest_backend.dto.order.GuestOrderItemRequest;
import com.stylenest.stylenest_backend.dto.order.GuestShippingAddressRequest;
import com.stylenest.stylenest_backend.dto.payment.EasebuzzInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.EasebuzzInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.GuestPaymentInitiateRequest;
import com.stylenest.stylenest_backend.entity.Address;
import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.InvalidPaymentSignatureException;
import com.stylenest.stylenest_backend.exception.PaymentGatewayException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.repository.CartRepository;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.service.EasebuzzClient;
import com.stylenest.stylenest_backend.service.EasebuzzHashService;
import com.stylenest.stylenest_backend.service.OrderService;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private EasebuzzClient easebuzzClient;

    private EasebuzzHashService hashService;

    private PaymentServiceImpl paymentService;

    private User user;
    private Address address;
    private Order order;

    @BeforeEach
    void setUp() {

        hashService = new EasebuzzHashService();

        paymentService = new PaymentServiceImpl(
                orderService, orderRepository, cartRepository, hashService, easebuzzClient);

        ReflectionTestUtils.setField(paymentService, "merchantKey", "TESTKEY");
        ReflectionTestUtils.setField(paymentService, "salt", "TESTSALT");
        ReflectionTestUtils.setField(paymentService, "env", "test");
        ReflectionTestUtils.setField(paymentService, "appBaseUrl", "https://api.example.com");
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "https://shop.example.com");

        user = User.builder().id(1L).email("customer@example.com").fullName("Customer").build();

        address = Address.builder()
                .id(10L)
                .fullName("Customer Name")
                .phone("9999999999")
                .addressLine1("123 Test St")
                .city("Testville")
                .state("TS")
                .country("India")
                .postalCode("123456")
                .build();

        order = buildOrder(PaymentMethod.CARD, PaymentStatus.PENDING);
    }

    private Order buildOrder(PaymentMethod method, PaymentStatus status) {

        return Order.builder()
                .id(99L)
                .orderNumber("F21-1000-ABCDEF")
                .user(user)
                .address(address)
                // Shipping snapshot -- what OrderServiceImpl.reserveOrder
                // would actually have captured at reservation time. Kept
                // equal to `address` here on purpose; the dedicated
                // regression test below deliberately diverges them.
                .shippingFullName(address.getFullName())
                .shippingPhone(address.getPhone())
                .shippingAddressLine1(address.getAddressLine1())
                .shippingCity(address.getCity())
                .shippingState(address.getState())
                .shippingCountry(address.getCountry())
                .shippingPostalCode(address.getPostalCode())
                .totalAmount(new BigDecimal("2799.00"))
                .paymentMethod(method)
                .paymentStatus(status)
                .orderItems(new ArrayList<>())
                .build();
    }

    @Test
    void initiate_rejectsCod() {

        EasebuzzInitiateRequest request = EasebuzzInitiateRequest.builder()
                .paymentMethod(PaymentMethod.COD)
                .build();

        assertThatThrownBy(() -> paymentService.initiate(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void initiate_rejectsUpiWithoutUpiVa() {

        EasebuzzInitiateRequest request = EasebuzzInitiateRequest.builder()
                .paymentMethod(PaymentMethod.UPI)
                .build();

        assertThatThrownBy(() -> paymentService.initiate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("upiVa");
    }

    @Test
    void initiate_rejectsNetbankingWithoutBankCode() {

        EasebuzzInitiateRequest request = EasebuzzInitiateRequest.builder()
                .paymentMethod(PaymentMethod.NETBANKING)
                .build();

        assertThatThrownBy(() -> paymentService.initiate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bankCode");
    }

    @Test
    void initiate_whenGatewayNotConfigured_failsCleanly() {

        ReflectionTestUtils.setField(paymentService, "merchantKey", "");

        EasebuzzInitiateRequest request = EasebuzzInitiateRequest.builder()
                .paymentMethod(PaymentMethod.CARD)
                .build();

        assertThatThrownBy(() -> paymentService.initiate(request))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void initiate_card_usesOrderAmountAndReturnsHostedRedirectUrl() {

        when(orderService.reserveOrderForOnlinePayment(PaymentMethod.CARD)).thenReturn(order);
        when(easebuzzClient.initiateLink(anyMap())).thenReturn("a".repeat(64));

        EasebuzzInitiateRequest request = EasebuzzInitiateRequest.builder()
                .paymentMethod(PaymentMethod.CARD)
                .build();

        EasebuzzInitiateResponse response = paymentService.initiate(request);

        assertThat(response.getOrderId()).isEqualTo(99L);
        assertThat(response.getAmount()).isEqualByComparingTo("2799.00");
        assertThat(response.getRedirectUrl()).isEqualTo("https://testpay.easebuzz.in/pay/" + "a".repeat(64));
        assertThat(response.getBankRedirectHtml()).isNull();

        // The amount sent to Easebuzz must be the server-side order amount,
        // never something the client could have supplied.
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(easebuzzClient).initiateLink((Map<String, String>) captor.capture());
        assertThat(captor.getValue().get("amount")).isEqualTo("2799.00");
        assertThat(captor.getValue().get("txnid")).isEqualTo("F21-1000-ABCDEF");
        assertThat(captor.getValue()).doesNotContainKey("request_flow");
    }

    @Test
    void buildInitiateParams_usesShippingSnapshotNotLiveAddress() {

        // Deliberately diverge the snapshot from the live Address row --
        // simulates the customer having since edited their saved address.
        // The payment gateway must see the ORIGINAL data captured at
        // order-reservation time, not whatever the live row says now.
        Address editedAddress = Address.builder()
                .id(10L)
                .fullName("Edited Later Name")
                .phone("8888888888")
                .addressLine1("999 Edited Ave")
                .city("Editedville")
                .state("ED")
                .country("India")
                .postalCode("999999")
                .build();

        order.setAddress(editedAddress); // live row now differs from the snapshot

        when(orderService.reserveOrderForOnlinePayment(PaymentMethod.CARD)).thenReturn(order);
        when(easebuzzClient.initiateLink(anyMap())).thenReturn("a".repeat(64));

        paymentService.initiate(EasebuzzInitiateRequest.builder().paymentMethod(PaymentMethod.CARD).build());

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(easebuzzClient).initiateLink((Map<String, String>) captor.capture());

        assertThat(captor.getValue().get("firstname")).isEqualTo("Customer Name"); // snapshot, not "Edited Later Name"
        assertThat(captor.getValue().get("phone")).isEqualTo("9999999999"); // snapshot, not "8888888888"
        assertThat(captor.getValue().get("city")).isEqualTo("Testville"); // snapshot, not "Editedville"
    }

    @Test
    void initiate_upi_setsSeamlessRequestFlowAndUpiFields() {

        Order upiOrder = buildOrder(PaymentMethod.UPI, PaymentStatus.PENDING);

        when(orderService.reserveOrderForOnlinePayment(PaymentMethod.UPI)).thenReturn(upiOrder);
        when(easebuzzClient.initiateLink(anyMap())).thenReturn("b".repeat(64));
        when(easebuzzClient.initiateSeamlessPayment(anyMap()))
                .thenReturn(EasebuzzClient.SeamlessResult.json(Map.of("status", (Object) 1)));

        EasebuzzInitiateRequest request = EasebuzzInitiateRequest.builder()
                .paymentMethod(PaymentMethod.UPI)
                .upiVa("customer@okhdfcbank")
                .build();

        EasebuzzInitiateResponse response = paymentService.initiate(request);

        assertThat(response.getMessage()).containsIgnoringCase("UPI app");
        assertThat(response.getRedirectUrl()).isNull();

        @SuppressWarnings("unchecked")
        var initiateCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(easebuzzClient).initiateLink((Map<String, String>) initiateCaptor.capture());
        assertThat(initiateCaptor.getValue().get("request_flow")).isEqualTo("SEAMLESS");

        @SuppressWarnings("unchecked")
        var seamlessCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(easebuzzClient).initiateSeamlessPayment((Map<String, String>) seamlessCaptor.capture());
        assertThat(seamlessCaptor.getValue()).containsEntry("payment_mode", "UPI");
        assertThat(seamlessCaptor.getValue()).containsEntry("upi_va", "customer@okhdfcbank");
    }

    // --- Guest initiate ---

    private GuestPaymentInitiateRequest guestInitiateRequest(PaymentMethod method) {

        return GuestPaymentInitiateRequest.builder()
                .guestEmail("guest@example.com")
                .paymentMethod(method)
                .upiVa(method == PaymentMethod.UPI ? "guest@okhdfcbank" : null)
                .shippingAddress(GuestShippingAddressRequest.builder()
                        .fullName("Guest Customer")
                        .phone("9998887777")
                        .addressLine1("1 Guest Rd")
                        .city("Guest City")
                        .state("GS")
                        .postalCode("111111")
                        .country("India")
                        .build())
                .items(List.of(GuestOrderItemRequest.builder().productVariantId(1L).quantity(1).build()))
                .build();
    }

    @Test
    void initiateForGuest_card_reachesEasebuzzUsingGuestOrder() {

        Order guestOrder = Order.builder()
                .id(101L)
                .orderNumber("F21-GUEST-1")
                .user(null)
                .guestEmail("guest@example.com")
                .shippingFullName("Guest Customer")
                .shippingPhone("9998887777")
                .shippingAddressLine1("1 Guest Rd")
                .shippingCity("Guest City")
                .shippingState("GS")
                .shippingCountry("India")
                .shippingPostalCode("111111")
                .totalAmount(new BigDecimal("500.00"))
                .paymentMethod(PaymentMethod.CARD)
                .paymentStatus(PaymentStatus.PENDING)
                .orderItems(new ArrayList<>())
                .build();

        when(orderService.reserveGuestOrderForOnlinePayment(any())).thenReturn(guestOrder);
        when(easebuzzClient.initiateLink(anyMap())).thenReturn("c".repeat(64));

        EasebuzzInitiateResponse response = paymentService.initiateForGuest(guestInitiateRequest(PaymentMethod.CARD));

        assertThat(response.getOrderId()).isEqualTo(101L);
        assertThat(response.getRedirectUrl()).isEqualTo("https://testpay.easebuzz.in/pay/" + "c".repeat(64));

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(easebuzzClient).initiateLink((Map<String, String>) captor.capture());
        assertThat(captor.getValue().get("email")).isEqualTo("guest@example.com"); // no User -- falls back to guestEmail
    }

    @Test
    void handleCallback_rejectsInvalidHash() {

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("txnid", "F21-1000-ABCDEF");
        response.put("hash", "not-a-real-hash");

        assertThatThrownBy(() -> paymentService.handleCallback(response))
                .isInstanceOf(InvalidPaymentSignatureException.class);

        verify(orderRepository, never()).findByOrderNumber(any());
    }

    @Test
    void handleCallback_whenOrderMissing_throwsNotFound() {

        Map<String, String> response = validSignedResponse("failure");

        when(orderRepository.findByOrderNumber("F21-1000-ABCDEF")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.handleCallback(response))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleCallback_success_marksPaidAndClearsCart() {

        Map<String, String> response = validSignedResponse("success");

        when(orderRepository.findByOrderNumber("F21-1000-ABCDEF")).thenReturn(Optional.of(order));

        // orderService is a mock -- markOnlinePaymentPaid() won't actually
        // mutate `order` unless we tell it to, same as the real
        // OrderServiceImpl.markOnlinePaymentPaid does.
        org.mockito.Mockito.doAnswer(inv -> {
            order.setPaymentStatus(PaymentStatus.PAID);
            return null;
        }).when(orderService).markOnlinePaymentPaid(order);

        Cart cart = Cart.builder().id(5L).user(user).items(new ArrayList<>()).build();
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));

        String redirect = paymentService.handleCallback(response);

        verify(orderService).markOnlinePaymentPaid(order);
        verify(orderService, never()).markOnlinePaymentFailed(any());
        verify(cartRepository).save(cart);
        assertThat(redirect).contains("/orders/99").contains("payment=success");
    }

    @Test
    void handleCallback_success_guestOrder_skipsCartClearWithoutError() {

        Order guestOrder = buildOrder(PaymentMethod.CARD, PaymentStatus.PENDING);
        guestOrder.setUser(null);
        guestOrder.setGuestEmail("guest@example.com");

        Map<String, String> response = validSignedResponse("success");

        when(orderRepository.findByOrderNumber("F21-1000-ABCDEF")).thenReturn(Optional.of(guestOrder));

        org.mockito.Mockito.doAnswer(inv -> {
            guestOrder.setPaymentStatus(PaymentStatus.PAID);
            return null;
        }).when(orderService).markOnlinePaymentPaid(guestOrder);

        assertThatCode(() -> paymentService.handleCallback(response)).doesNotThrowAnyException();

        verify(cartRepository, never()).findByUser(any());
    }

    @Test
    void handleCallback_failure_marksFailedAndLeavesCartAlone() {

        Map<String, String> response = validSignedResponse("failure");

        when(orderRepository.findByOrderNumber("F21-1000-ABCDEF")).thenReturn(Optional.of(order));

        org.mockito.Mockito.doAnswer(inv -> {
            order.setPaymentStatus(PaymentStatus.FAILED);
            return null;
        }).when(orderService).markOnlinePaymentFailed(order);

        String redirect = paymentService.handleCallback(response);

        verify(orderService).markOnlinePaymentFailed(order);
        verify(orderService, never()).markOnlinePaymentPaid(any());
        verify(cartRepository, never()).findByUser(any());
        assertThat(redirect).contains("/orders/99").contains("payment=failed");
    }

    @Test
    void handleCallback_duplicateCallbackAfterAlreadyPaid_isIdempotentNoOp() {

        Order alreadyPaid = buildOrder(PaymentMethod.CARD, PaymentStatus.PAID);

        Map<String, String> response = validSignedResponse("success");

        when(orderRepository.findByOrderNumber("F21-1000-ABCDEF")).thenReturn(Optional.of(alreadyPaid));

        paymentService.handleCallback(response);

        verify(orderService, never()).markOnlinePaymentPaid(any());
        verify(orderService, never()).markOnlinePaymentFailed(any());
        verify(cartRepository, never()).findByUser(any());
    }

    /**
     * Builds a callback payload whose "hash" field is a genuine, correctly
     * computed reverse hash for the given status. The hash is computed here
     * independently of EasebuzzHashService (plain MessageDigest, same
     * documented field sequence) so this fixture doesn't depend on the
     * production hash code being correct -- PaymentServiceImpl still calls
     * the real EasebuzzHashService.verifyResponseHash internally, so this
     * genuinely exercises that verification path.
     */
    private Map<String, String> validSignedResponse(String status) {

        Map<String, String> fields = new HashMap<>();
        fields.put("status", status);
        fields.put("txnid", order.getOrderNumber());
        fields.put("key", "TESTKEY");
        fields.put("amount", "2799.00");
        fields.put("firstname", "Customer Name");
        fields.put("email", "customer@example.com");
        fields.put("productinfo", "StyleNest Order");

        java.util.List<String> reverseOrderFields = java.util.List.of(
                "udf10", "udf9", "udf8", "udf7", "udf6", "udf5", "udf4", "udf3", "udf2", "udf1",
                "email", "firstname", "productinfo", "amount", "txnid", "key");

        StringBuilder sb = new StringBuilder("TESTSALT").append('|').append(status);

        for (String field : reverseOrderFields) {
            sb.append('|').append(fields.getOrDefault(field, ""));
        }

        fields.put("hash", sha512Hex(sb.toString()));

        return fields;
    }

    private String sha512Hex(String input) {

        try {

            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-512");
            byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
