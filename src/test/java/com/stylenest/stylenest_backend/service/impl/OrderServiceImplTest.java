package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.stylenest.stylenest_backend.dto.order.GuestOrderItemRequest;
import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.order.GuestShippingAddressRequest;
import com.stylenest.stylenest_backend.dto.order.OrderRequest;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.entity.Address;
import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.CartItem;
import com.stylenest.stylenest_backend.entity.Invoice;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.InsufficientStockException;
import com.stylenest.stylenest_backend.exception.PendingPaymentExistsException;
import com.stylenest.stylenest_backend.exception.UnsupportedPaymentCurrencyException;
import com.stylenest.stylenest_backend.mapper.OrderMapper;
import com.stylenest.stylenest_backend.repository.AddressRepository;
import com.stylenest.stylenest_backend.repository.CartRepository;
import com.stylenest.stylenest_backend.repository.InvoiceRepository;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.ShipmentHistoryRepository;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.InvoiceGenerationService;
import com.stylenest.stylenest_backend.service.InvoiceService;
import com.stylenest.stylenest_backend.service.ProductPricingService;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentHistoryRepository shipmentHistoryRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private InvoiceGenerationService invoiceGenerationService;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private ProductPricingService productPricingService;

    private OrderMapper orderMapper;

    private OrderServiceImpl orderService;

    private User user;
    private Address address;
    private ProductVariant variant;
    private Cart cart;

    @BeforeEach
    void setUp() {

        orderMapper = new OrderMapper(shipmentRepository, invoiceRepository);

        orderService = new OrderServiceImpl(
                userRepository, productVariantRepository, cartRepository,
                addressRepository, orderRepository, shipmentRepository,
                shipmentHistoryRepository, orderMapper, invoiceService, invoiceGenerationService, emailService,
                productPricingService);

        user = User.builder().id(1L).email("customer@example.com").fullName("Customer").build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));

        // lenient: not every test reaches getCurrentUser() (e.g. the
        // COD-rejection and already-terminal-status tests bail out earlier)
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        // lenient: shipment creation happens on every successful COD/paid
        // reservation, but not every test reaches that point.
        lenient().when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

        address = Address.builder().id(10L).fullName("Customer").phone("9999999999").isDefault(true).build();

        Product product = Product.builder().id(1L).name("Test Dress").price(new BigDecimal("1000.00")).build();

        variant = ProductVariant.builder()
                .id(1L)
                .product(product)
                .stock(5)
                .color("BLACK")
                .size(Size.M)
                .build();

        CartItem cartItem = CartItem.builder()
                .id(1L)
                .productVariant(variant)
                .quantity(2)
                .price(new BigDecimal("1000.00"))
                .build();

        cart = Cart.builder().id(1L).user(user).items(new ArrayList<>(List.of(cartItem))).build();

        // Guests have no cart-item price snapshot to reuse, so
        // linesFromGuestRequest resolves fresh through this service --
        // mirror the real INR rule (discountPrice-else-price) here rather
        // than hardcoding a single stubbed value, since some tests change
        // the product's discountPrice after setUp.
        lenient().when(productPricingService.resolvePrice(any(Product.class), eq(Currency.INR)))
                .thenAnswer(inv -> {
                    Product p = inv.getArgument(0);
                    return Optional.of(new ProductPricingService.ResolvedPrice(p.getPrice(), p.getDiscountPrice()));
                });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void placeOrder_rejectsNonCodPaymentMethod() {

        OrderRequest request = OrderRequest.builder().paymentMethod(PaymentMethod.ONLINE).build();

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("/api/payments/initiate");
    }

    @Test
    void placeOrder_cod_decrementsStockClearsCartAndStaysPending() {

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.of(address));
        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderRequest request = OrderRequest.builder().paymentMethod(PaymentMethod.COD).build();

        OrderResponse response = orderService.placeOrder(request);

        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("2000.00");
        assertThat(variant.getStock()).isEqualTo(3); // 5 - 2

        assertThat(cart.getItems()).isEmpty(); // COD clears the cart immediately
        verify(cartRepository).save(cart);

        // COD is fulfillment-guaranteed immediately -- a shipment is created
        // right away, not deferred to a later payment-verified step.
        verify(shipmentRepository).save(any(Shipment.class));
        verify(shipmentHistoryRepository).save(any());
    }

    @Test
    void placeOrder_cod_capturesShippingSnapshotFromAddress() {

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.of(address));
        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice invoice = Invoice.builder().id(1L).build();
        when(invoiceGenerationService.generateForRetailOrder(any(Order.class))).thenReturn(invoice);
        when(invoiceService.generatePdf(invoice)).thenReturn(new byte[] { 1, 2, 3 });

        orderService.placeOrder(OrderRequest.builder().paymentMethod(PaymentMethod.COD).build());

        // 2 saves: the reservation itself, then confirmationEmailSent=true
        // once sendConfirmationEmailIfNeeded succeeds -- both persist the
        // same Order instance, so the snapshot is present from the first.
        org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(captor.capture());

        Order saved = captor.getAllValues().get(0);
        assertThat(saved.getShippingFullName()).isEqualTo(address.getFullName());
        assertThat(saved.getShippingPhone()).isEqualTo(address.getPhone());
        assertThat(saved.getAddress()).isSameAs(address); // kept for admin backward-nav
    }

    @Test
    void placeOrder_insufficientStock_throwsAndDoesNotClearCart() {

        variant.setStock(1); // less than the cart's quantity of 2

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.of(address));
        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));

        OrderRequest request = OrderRequest.builder().paymentMethod(PaymentMethod.COD).build();

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(cart.getItems()).isNotEmpty();
        verify(cartRepository, never()).save(any());
    }

    @Test
    void placeOrder_usdCart_blockedBeforeAnyPersistence() {

        cart.setCurrency(Currency.USD);

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.of(address));

        OrderRequest request = OrderRequest.builder().paymentMethod(PaymentMethod.COD).build();

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(UnsupportedPaymentCurrencyException.class);

        // Blocked before any stock decrement, Order row, or Cashfree contact.
        verify(orderRepository, never()).save(any());
        verify(productVariantRepository, never()).save(any());
        assertThat(variant.getStock()).isEqualTo(5); // untouched
    }

    @Test
    void reserveOrderForOnlinePayment_usdCart_blockedBeforeAnyPersistence() {

        cart.setCurrency(Currency.USD);

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.of(address));
        when(orderRepository.findByUserAndOrderStatusAndPaymentMethodNot(user, OrderStatus.PENDING, PaymentMethod.COD))
                .thenReturn(List.of());

        assertThatThrownBy(() -> orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE))
                .isInstanceOf(UnsupportedPaymentCurrencyException.class);

        verify(orderRepository, never()).save(any());
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void reserveOrderForOnlinePayment_rejectsCod() {

        assertThatThrownBy(() -> orderService.reserveOrderForOnlinePayment(PaymentMethod.COD))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void reserveOrderForOnlinePayment_reservesStockButLeavesCartUntouched() {

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findByUserAndIsDefaultTrue(user)).thenReturn(Optional.of(address));
        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(orderRepository.findByUserAndOrderStatusAndPaymentMethodNot(user, OrderStatus.PENDING, PaymentMethod.COD))
                .thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE);

        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.ONLINE);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("2000.00");
        assertThat(variant.getStock()).isEqualTo(3); // stock reserved immediately

        assertThat(cart.getItems()).isNotEmpty(); // NOT cleared yet -- only on verified payment
        verify(cartRepository, never()).save(any());

        // Online payment: no shipment until payment is verified PAID.
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void reserveOrderForOnlinePayment_sameCartAsExistingPendingOrder_reusesIt() {

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));

        Order existing = Order.builder()
                .id(50L)
                .user(user)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PENDING)
                .orderStatus(OrderStatus.PENDING)
                .orderItems(new ArrayList<>(List.of(
                        OrderItem.builder().productVariant(variant).quantity(2).price(new BigDecimal("1000.00")).build())))
                .totalAmount(new BigDecimal("2000.00"))
                .build();

        when(orderRepository.findByUserAndOrderStatusAndPaymentMethodNot(user, OrderStatus.PENDING, PaymentMethod.COD))
                .thenReturn(List.of(existing));

        Order result = orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE);

        assertThat(result).isSameAs(existing);
        verify(orderRepository, never()).save(any());
        verify(productVariantRepository, never()).findById(any());
        assertThat(variant.getStock()).isEqualTo(5); // untouched -- not re-decremented
    }

    @Test
    void reserveOrderForOnlinePayment_differentCartThanExistingPendingOrder_throwsConflict() {

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));

        ProductVariant otherVariant = ProductVariant.builder().id(2L).stock(10).build();

        Order existing = Order.builder()
                .id(50L)
                .user(user)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PENDING)
                .orderStatus(OrderStatus.PENDING)
                .orderItems(new ArrayList<>(List.of(
                        OrderItem.builder().productVariant(otherVariant).quantity(1).price(BigDecimal.TEN).build())))
                .totalAmount(BigDecimal.TEN)
                .build();

        when(orderRepository.findByUserAndOrderStatusAndPaymentMethodNot(user, OrderStatus.PENDING, PaymentMethod.COD))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> orderService.reserveOrderForOnlinePayment(PaymentMethod.ONLINE))
                .isInstanceOf(PendingPaymentExistsException.class);
    }

    @Test
    void markOnlinePaymentPaid_setsPaidStatus_createsShipment_sendsEmailOnce() {

        Order order = Order.builder()
                .id(1L)
                .orderNumber("F21-1")
                .paymentStatus(PaymentStatus.PENDING)
                .user(user)
                .shippingFullName("Customer")
                .orderItems(new ArrayList<>())
                .confirmationEmailSent(false)
                .build();

        Invoice invoice = Invoice.builder().id(1L).invoiceNumber("INV-000001").build();
        when(invoiceGenerationService.generateForRetailOrder(order)).thenReturn(invoice);
        when(invoiceService.generatePdf(invoice)).thenReturn(new byte[] { 1, 2, 3 });

        orderService.markOnlinePaymentPaid(order);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getConfirmationEmailSent()).isTrue();

        verify(shipmentRepository).save(any(Shipment.class));
        verify(shipmentHistoryRepository).save(any());
        verify(emailService).sendOrderConfirmationEmail(
                org.mockito.ArgumentMatchers.eq(user.getEmail()), any(), any());

        // order.getPaymentStatus() write + confirmationEmailSent write
        verify(orderRepository, times(2)).save(order);
    }

    @Test
    void markOnlinePaymentPaid_emailFailure_doesNotPreventPaymentStatusFromBeingSaved() {

        Order order = Order.builder()
                .id(1L)
                .orderNumber("F21-1")
                .paymentStatus(PaymentStatus.PENDING)
                .user(user)
                .shippingFullName("Customer")
                .orderItems(new ArrayList<>())
                .confirmationEmailSent(false)
                .build();

        when(invoiceGenerationService.generateForRetailOrder(order))
                .thenThrow(new RuntimeException("PDF generation blew up"));

        // Must not throw -- a PDF/email failure is swallowed, never
        // propagated back to the caller (which would roll back the
        // enclosing transaction, including the paymentStatus write above).
        orderService.markOnlinePaymentPaid(order);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getConfirmationEmailSent()).isNotEqualTo(true);

        verify(emailService, never()).sendOrderConfirmationEmail(any(), any(), any());
    }

    @Test
    void markOnlinePaymentPaid_alreadyPaid_isIdempotentNoOp() {

        Order order = Order.builder().id(1L).paymentStatus(PaymentStatus.PAID).build();

        orderService.markOnlinePaymentPaid(order);

        verify(orderRepository, never()).save(any());
        verify(shipmentRepository, never()).save(any());
        verify(emailService, never()).sendOrderConfirmationEmail(any(), any(), any());
    }

    @Test
    void markOnlinePaymentFailed_restoresStockAndCancelsOrder() {

        Order order = Order.builder()
                .id(1L)
                .paymentStatus(PaymentStatus.PENDING)
                .orderStatus(OrderStatus.PENDING)
                .orderItems(new ArrayList<>(List.of(
                        OrderItem.builder().productVariant(variant).quantity(2).price(BigDecimal.TEN).build())))
                .build();

        orderService.markOnlinePaymentFailed(order);

        assertThat(variant.getStock()).isEqualTo(7); // 5 + 2 restored
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void markOnlinePaymentFailed_alreadyCancelled_isIdempotentNoOp() {

        Order order = Order.builder()
                .id(1L)
                .orderStatus(OrderStatus.CANCELLED)
                .paymentStatus(PaymentStatus.FAILED)
                .orderItems(new ArrayList<>())
                .build();

        orderService.markOnlinePaymentFailed(order);

        verify(orderRepository, never()).save(any());
        verify(productVariantRepository, never()).save(any());
    }

    // --- Guest checkout ---

    private GuestOrderRequest guestRequest(PaymentMethod method) {

        return GuestOrderRequest.builder()
                .guestEmail("guest@example.com")
                .paymentMethod(method)
                .shippingAddress(GuestShippingAddressRequest.builder()
                        .fullName("Guest Customer")
                        .phone("9998887777")
                        .addressLine1("123 Guest St")
                        .city("Metropolis")
                        .state("State")
                        .postalCode("100001")
                        .country("India")
                        .build())
                .items(List.of(GuestOrderItemRequest.builder()
                        .productVariantId(1L)
                        .quantity(2)
                        .build()))
                .currency(Currency.INR)
                .build();
    }

    @Test
    void placeGuestOrder_usdCurrency_blockedBeforeAnyPersistence() {

        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        lenient().when(productPricingService.resolvePrice(any(Product.class), eq(Currency.USD)))
                .thenReturn(Optional.of(new ProductPricingService.ResolvedPrice(new BigDecimal("69.00"), null)));

        GuestOrderRequest request = GuestOrderRequest.builder()
                .guestEmail("guest@example.com")
                .paymentMethod(PaymentMethod.COD)
                .shippingAddress(GuestShippingAddressRequest.builder()
                        .fullName("Guest Customer")
                        .phone("9998887777")
                        .addressLine1("123 Guest St")
                        .city("Metropolis")
                        .state("State")
                        .postalCode("100001")
                        .country("India")
                        .build())
                .items(List.of(GuestOrderItemRequest.builder().productVariantId(1L).quantity(2).build()))
                .currency(Currency.USD)
                .build();

        assertThatThrownBy(() -> orderService.placeGuestOrder(request))
                .isInstanceOf(UnsupportedPaymentCurrencyException.class);

        verify(orderRepository, never()).save(any());
        verify(productVariantRepository, never()).save(any());
        assertThat(variant.getStock()).isEqualTo(5); // untouched
    }

    @Test
    void placeGuestOrder_rejectsNonCod() {

        assertThatThrownBy(() -> orderService.placeGuestOrder(guestRequest(PaymentMethod.ONLINE)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("/api/payments/guest/initiate");
    }

    @Test
    void placeGuestOrder_cod_reservesStockAndSendsConfirmationToGuestEmail() {

        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice invoice = Invoice.builder().id(1L).build();
        when(invoiceGenerationService.generateForRetailOrder(any(Order.class))).thenReturn(invoice);
        when(invoiceService.generatePdf(invoice)).thenReturn(new byte[] { 1, 2, 3 });

        OrderResponse response = orderService.placeGuestOrder(guestRequest(PaymentMethod.COD));

        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(variant.getStock()).isEqualTo(3); // 5 - 2

        // Guest orders never had a server-side cart to clear.
        verify(cartRepository, never()).save(any());

        verify(emailService).sendOrderConfirmationEmail(
                org.mockito.ArgumentMatchers.eq("guest@example.com"), any(), any());
    }

    @Test
    void placeGuestOrder_usesDiscountPriceWhenPresent_sameRuleAsCart() {

        variant.getProduct().setDiscountPrice(new BigDecimal("799.00"));

        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.placeGuestOrder(guestRequest(PaymentMethod.COD));

        assertThat(response.getTotalAmount()).isEqualByComparingTo("1598.00"); // 799 * 2
    }

    @Test
    void reserveGuestOrderForOnlinePayment_reservesStockLeavesNoCartInteraction() {

        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(orderRepository.findByGuestEmailAndOrderStatusAndPaymentMethodNot(
                "guest@example.com", OrderStatus.PENDING, PaymentMethod.COD))
                .thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.reserveGuestOrderForOnlinePayment(guestRequest(PaymentMethod.ONLINE));

        assertThat(order.getUser()).isNull();
        assertThat(order.getGuestEmail()).isEqualTo("guest@example.com");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(variant.getStock()).isEqualTo(3);

        verify(cartRepository, never()).findByUser(any());
        verify(shipmentRepository, never()).save(any()); // online: shipment only after verified payment
    }

    @Test
    void reserveGuestOrderForOnlinePayment_sameItemsAsExistingPending_reusesIt() {

        Order existing = Order.builder()
                .id(60L)
                .guestEmail("guest@example.com")
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.PENDING)
                .orderStatus(OrderStatus.PENDING)
                .orderItems(new ArrayList<>(List.of(
                        OrderItem.builder().productVariant(variant).quantity(2).price(new BigDecimal("1000.00")).build())))
                .totalAmount(new BigDecimal("2000.00"))
                .build();

        when(orderRepository.findByGuestEmailAndOrderStatusAndPaymentMethodNot(
                "guest@example.com", OrderStatus.PENDING, PaymentMethod.COD))
                .thenReturn(List.of(existing));

        // linesFromGuestRequest always looks the variant up to price the
        // request (guests have no pre-existing cart-item price snapshot to
        // reuse) -- even when the reservation ends up being a re-use, not a
        // new one. What must NOT happen on reuse is stock being touched
        // again.
        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));

        Order result = orderService.reserveGuestOrderForOnlinePayment(guestRequest(PaymentMethod.ONLINE));

        assertThat(result).isSameAs(existing);
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void validateOwnership_guestOrder_registeredCustomerCannotAccessIt() {

        // order.getUser() is null (a guest order) -- getOrderById must
        // reject cleanly instead of NPEing on order.getUser().getId().
        Order guestOrder = Order.builder()
                .id(99L)
                .guestEmail("someoneelse@example.com")
                .orderItems(new ArrayList<>())
                .build();

        when(orderRepository.findById(99L)).thenReturn(Optional.of(guestOrder));

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(com.stylenest.stylenest_backend.exception.UnauthorizedAccessException.class);
    }
}
