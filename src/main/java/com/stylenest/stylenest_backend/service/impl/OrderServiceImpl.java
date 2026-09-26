package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.email.OrderConfirmationEmailData;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceItemResponse;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.dto.order.GuestOrderItemRequest;
import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.order.GuestShippingAddressRequest;
import com.stylenest.stylenest_backend.dto.order.OrderRequest;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.dto.order.OrderSummaryResponse;
import com.stylenest.stylenest_backend.entity.Address;
import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.Invoice;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.entity.ShipmentHistory;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.InsufficientStockException;
import com.stylenest.stylenest_backend.exception.PendingPaymentExistsException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.exception.UnauthorizedAccessException;
import com.stylenest.stylenest_backend.exception.UnsupportedPaymentCurrencyException;
import com.stylenest.stylenest_backend.mapper.OrderMapper;
import com.stylenest.stylenest_backend.repository.AddressRepository;
import com.stylenest.stylenest_backend.repository.CartRepository;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.ShipmentHistoryRepository;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.InvoiceGenerationService;
import com.stylenest.stylenest_backend.service.InvoiceService;
import com.stylenest.stylenest_backend.service.OrderService;
import com.stylenest.stylenest_backend.service.ProductPricingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentHistoryRepository shipmentHistoryRepository;
    private final OrderMapper orderMapper;
    private final InvoiceService invoiceService;
    private final InvoiceGenerationService invoiceGenerationService;
    private final EmailService emailService;
    private final ProductPricingService productPricingService;

    private User getCurrentUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found."));
    }

    private String generateOrderNumber() {

        return "SN-" +
                System.currentTimeMillis() +
                "-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 6)
                        .toUpperCase();
    }

    @Override
    public OrderResponse placeOrder(OrderRequest request) {

        // Online payments must go through the Easebuzz flow, which needs to
        // reserve the order *before* the customer pays and only clear the
        // cart once payment is verified. COD keeps its original, simpler
        // one-call behavior: reserve and finish in the same request.
        if (request.getPaymentMethod() != PaymentMethod.COD) {

            throw new BadRequestException(
                    "Online payments must be started via "
                            + "POST /api/payments/easebuzz/initiate. "
                            + "This endpoint only accepts COD.");
        }

        User user = getCurrentUser();

        Cart cart = getNonEmptyCart(user);

        Address address = getDefaultAddress(user);

        Order order = reserveOrder(
                user, null, address, ShippingSnapshot.fromAddress(address),
                linesFromCart(cart), PaymentMethod.COD, cart.getCurrency());

        cart.getItems().clear();
        cart.setTotalPrice(BigDecimal.ZERO);

        cartRepository.save(cart);

        sendConfirmationEmailIfNeeded(order);

        return orderMapper.toResponse(order);
    }

    @Override
    public Order reserveOrderForOnlinePayment(PaymentMethod paymentMethod) {

        if (paymentMethod == PaymentMethod.COD) {
            throw new BadRequestException("COD does not go through the payment gateway.");
        }

        User user = getCurrentUser();

        Cart cart = getNonEmptyCart(user);

        List<ReservationLine> lines = linesFromCart(cart);

        List<Order> inProgress = orderRepository
                .findByUserAndOrderStatusAndPaymentMethodNot(user, OrderStatus.PENDING, PaymentMethod.COD)
                .stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PENDING)
                .toList();

        for (Order existing : inProgress) {
            if (matchesLines(existing, lines)) {
                // Same cart, same in-flight order -- re-use it (e.g. the
                // customer refreshed the payment page or double-clicked Pay)
                // instead of reserving stock a second time.
                return existing;
            }
        }

        if (!inProgress.isEmpty()) {
            throw new PendingPaymentExistsException(
                    "You already have a payment in progress for a different cart. "
                            + "Cancel it first via PUT /api/orders/{id}/cancel, then try again.");
        }

        Address address = getDefaultAddress(user);

        return reserveOrder(user, null, address, ShippingSnapshot.fromAddress(address), lines, paymentMethod, cart.getCurrency());
    }

    @Override
    public void markOnlinePaymentPaid(Order order) {

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return; // already applied -- idempotent against duplicate callbacks
        }

        order.setPaymentStatus(PaymentStatus.PAID);

        orderRepository.save(order);

        createInitialShipment(order);

        sendConfirmationEmailIfNeeded(order);
    }

    @Override
    public void markOnlinePaymentFailed(Order order) {

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            return; // already applied -- idempotent against duplicate callbacks
        }

        for (OrderItem item : order.getOrderItems()) {

            ProductVariant variant = item.getProductVariant();

            variant.setStock(variant.getStock() + item.getQuantity());

            productVariantRepository.save(variant);
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.FAILED);

        orderRepository.save(order);
    }

    @Override
    public OrderResponse placeGuestOrder(GuestOrderRequest request) {

        if (request.getPaymentMethod() != PaymentMethod.COD) {

            throw new BadRequestException(
                    "Online payments must be started via "
                            + "POST /api/payments/easebuzz/guest/initiate. "
                            + "This endpoint only accepts COD.");
        }

        validateGuestShippingAddress(request.getShippingAddress());

        List<ReservationLine> lines = linesFromGuestRequest(request.getItems(), request.getCurrency());

        ShippingSnapshot snapshot = ShippingSnapshot.fromGuestRequest(request.getShippingAddress());

        Order order = reserveOrder(
                null, request.getGuestEmail(), null, snapshot, lines, PaymentMethod.COD, request.getCurrency());

        sendConfirmationEmailIfNeeded(order);

        return orderMapper.toResponse(order);
    }

    @Override
    public Order reserveGuestOrderForOnlinePayment(GuestOrderRequest request) {

        if (request.getPaymentMethod() == PaymentMethod.COD) {
            throw new BadRequestException("COD does not go through the payment gateway.");
        }

        validateGuestShippingAddress(request.getShippingAddress());

        List<ReservationLine> lines = linesFromGuestRequest(request.getItems(), request.getCurrency());

        List<Order> inProgress = orderRepository
                .findByGuestEmailAndOrderStatusAndPaymentMethodNot(
                        request.getGuestEmail(), OrderStatus.PENDING, PaymentMethod.COD)
                .stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PENDING)
                .toList();

        for (Order existing : inProgress) {
            if (matchesLines(existing, lines)) {
                return existing;
            }
        }

        if (!inProgress.isEmpty()) {
            throw new PendingPaymentExistsException(
                    "You already have a payment in progress for a different order. "
                            + "Complete that payment first, then try again.");
        }

        ShippingSnapshot snapshot = ShippingSnapshot.fromGuestRequest(request.getShippingAddress());

        return reserveOrder(
                null, request.getGuestEmail(), null, snapshot, lines,
                request.getPaymentMethod(), request.getCurrency());
    }

    private Cart getNonEmptyCart(User user) {

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found."));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty.");
        }

        return cart;
    }

    private Address getDefaultAddress(User user) {

        return addressRepository
                .findByUserAndIsDefaultTrue(user)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Default address not found."));
    }

    private List<ReservationLine> linesFromCart(Cart cart) {

        return cart.getItems().stream()
                .map(cartItem -> new ReservationLine(
                        cartItem.getProductVariant().getId(),
                        cartItem.getQuantity(),
                        cartItem.getPrice()))
                .toList();
    }

    // Loose but real check: an optional leading "+" country code, then
    // 5-20 digits -- matches PaymentServiceImpl's own PHONE_PATTERN, since
    // this same number is what eventually gets sent to Easebuzz for
    // online payments and must not fail validation there instead.
    private static final Pattern GUEST_PHONE_PATTERN = Pattern.compile("^(\\+\\d{1,4}[-]?)?\\d{5,20}$");

    private void validateGuestShippingAddress(GuestShippingAddressRequest address) {

        String cleanedPhone = address.getPhone() == null
                ? ""
                : address.getPhone().replaceAll("[\\s()-]", "");

        if (!GUEST_PHONE_PATTERN.matcher(cleanedPhone).matches()) {
            throw new BadRequestException("Please enter a valid phone number.");
        }

        boolean isIndia = "IN".equalsIgnoreCase(address.getCountryCode())
                || (address.getCountryCode() == null && "India".equalsIgnoreCase(address.getCountry()));

        if (isIndia && !address.getPostalCode().matches("^[1-9][0-9]{5}$")) {
            throw new BadRequestException("Please enter a valid 6-digit PIN code.");
        }
    }

    private List<ReservationLine> linesFromGuestRequest(List<GuestOrderItemRequest> items, Currency currency) {

        return items.stream()
                .map(item -> {

                    ProductVariant variant = productVariantRepository.findById(item.getProductVariantId())
                            .orElseThrow(() -> new ResourceNotFoundException("Product Variant not found."));

                    // Guests have no server-side cart to snapshot a price
                    // from, so it's resolved fresh here, same as
                    // CartServiceImpl.addToCart does for registered carts.
                    BigDecimal price = productPricingService
                            .resolvePrice(variant.getProduct(), currency)
                            .orElseThrow(() -> new BadRequestException(
                                    "This product is not available in " + currency + " yet."))
                            .effectivePrice();

                    return new ReservationLine(item.getProductVariantId(), item.getQuantity(), price);
                })
                .toList();
    }

    private boolean matchesLines(Order order, List<ReservationLine> lines) {

        if (order.getOrderItems().size() != lines.size()) {
            return false;
        }

        Map<Long, Integer> orderedQuantities = order.getOrderItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getProductVariant().getId(),
                        OrderItem::getQuantity));

        for (ReservationLine line : lines) {

            Integer orderedQuantity = orderedQuantities.get(line.productVariantId());

            if (orderedQuantity == null || !orderedQuantity.equals(line.quantity())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validates stock, reserves it (decrements immediately so a second
     * customer can't oversell the same item while this one is on the
     * payment page), and persists the order + its items, with a shipping
     * address snapshot captured at this exact moment -- so a later edit to
     * a saved Address (or, for guests, the complete absence of one) can
     * never change how a historical order/invoice/email looks. Does NOT
     * touch the cart -- callers decide when that happens (COD: immediately;
     * online payment: only after the payment is verified). Creates the
     * initial Shipment immediately for COD (fulfillment is guaranteed);
     * online-payment shipments are created only once payment is verified
     * (see markOnlinePaymentPaid).
     *
     * All four public entry points (registered COD/online, guest COD/online)
     * converge here, which is why the USD block below -- thrown before any
     * Order row is persisted or stock is touched, and before Easebuzz can
     * ever be contacted -- covers every checkout path uniformly. Remove
     * this guard only once Razorpay is actually integrated for USD.
     */
    private Order reserveOrder(
            User user, String guestEmail, Address liveAddress,
            ShippingSnapshot snapshot, List<ReservationLine> lines, PaymentMethod paymentMethod,
            Currency currency) {

        if (currency == Currency.USD) {
            throw new UnsupportedPaymentCurrencyException(
                    "International online payments will be available soon. "
                            + "Please try again once international payment support is enabled.");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .user(user)
                .guestEmail(guestEmail)
                .address(liveAddress)
                .shippingFullName(snapshot.fullName())
                .shippingPhone(snapshot.phone())
                .shippingPhoneCountryCode(snapshot.phoneCountryCode())
                .shippingAddressLine1(snapshot.addressLine1())
                .shippingAddressLine2(snapshot.addressLine2())
                .shippingCity(snapshot.city())
                .shippingState(snapshot.state())
                .shippingPostalCode(snapshot.postalCode())
                .shippingCountry(snapshot.country())
                .shippingCountryCode(snapshot.countryCode())
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.PENDING)
                .orderStatus(OrderStatus.PENDING)
                .currency(currency)
                .build();

        for (ReservationLine line : lines) {

            ProductVariant variant = productVariantRepository
                    .findById(line.productVariantId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Product Variant not found."));

            if (variant.getStock() < line.quantity()) {

                throw new InsufficientStockException(
                        variant.getProduct().getName()
                                + " has only "
                                + variant.getStock()
                                + " item(s) left in stock.");
            }

            variant.setStock(
                    variant.getStock() - line.quantity());

            productVariantRepository.save(variant);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productVariant(variant)
                    .quantity(line.quantity())
                    .price(line.unitPrice())
                    .build();

            order.getOrderItems().add(orderItem);

            totalAmount = totalAmount.add(
                    line.unitPrice().multiply(
                            BigDecimal.valueOf(line.quantity())
                    )
            );
        }

        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        if (paymentMethod == PaymentMethod.COD) {
            createInitialShipment(savedOrder);
        }

        return savedOrder;
    }

    private void createInitialShipment(Order order) {

        Shipment shipment = Shipment.builder()
                .order(order)
                .shipmentStatus(ShipmentStatus.PROCESSING)
                .build();

        shipment = shipmentRepository.save(shipment);

        shipmentHistoryRepository.save(ShipmentHistory.builder()
                .shipment(shipment)
                .status(ShipmentStatus.PROCESSING)
                .description("Order confirmed; preparing shipment.")
                .build());
    }

    /**
     * Sends the order-confirmation email (with invoice PDF attached) at
     * most once per order. Called only once fulfillment is guaranteed:
     * immediately for COD, or once payment is verified PAID for online
     * orders -- never at mere reservation time.
     *
     * A failure here (Brevo down, PDF generation error, etc.) is
     * deliberately swallowed rather than rethrown: this method always runs
     * inside the same transaction as the order/payment write it follows,
     * and that write must stay durable regardless of whether the email
     * could be sent.
     */
    private void sendConfirmationEmailIfNeeded(Order order) {

        if (Boolean.TRUE.equals(order.getConfirmationEmailSent())) {
            return;
        }

        String recipient = order.getUser() != null ? order.getUser().getEmail() : order.getGuestEmail();

        if (recipient == null || recipient.isBlank()) {
            return;
        }

        try {

            Invoice invoice = invoiceGenerationService.generateForRetailOrder(order);
            byte[] invoicePdf = invoiceService.generatePdf(invoice);

            List<InvoiceItemResponse> items = order.getOrderItems().stream()
                    .map(item -> InvoiceItemResponse.builder()
                            .productName(item.getProductVariant().getProduct().getName())
                            .color(item.getProductVariant().getColor())
                            .size(item.getProductVariant().getSize().getLabel())
                            .quantity(item.getQuantity())
                            .price(item.getPrice())
                            .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .build())
                    .toList();

            BigDecimal subtotal = items.stream()
                    .map(InvoiceItemResponse::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            OrderConfirmationEmailData data = OrderConfirmationEmailData.builder()
                    .customerName(order.getShippingFullName())
                    .orderNumber(order.getOrderNumber())
                    .invoiceNumber(invoice.getInvoiceNumber())
                    .orderDate(order.getCreatedAt())
                    .items(items)
                    .subtotal(subtotal)
                    .totalAmount(order.getTotalAmount())
                    .paymentMethod(order.getPaymentMethod())
                    .paymentStatus(order.getPaymentStatus())
                    .orderStatus(order.getOrderStatus())
                    .shippingAddressLine1(order.getShippingAddressLine1())
                    .shippingAddressLine2(order.getShippingAddressLine2())
                    .shippingCity(order.getShippingCity())
                    .shippingState(order.getShippingState())
                    .shippingPostalCode(order.getShippingPostalCode())
                    .shippingCountry(order.getShippingCountry())
                    .build();

            emailService.sendOrderConfirmationEmail(recipient, data, invoicePdf);

            order.setConfirmationEmailSent(true);

            orderRepository.save(order);

        } catch (Exception ex) {

            ex.printStackTrace();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getMyOrders() {

        User user = getCurrentUser();

        return orderRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(orderMapper::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {

        User user = getCurrentUser();

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Order not found with id: " + id));

        validateOwnership(order, user);

        return orderMapper.toResponse(order);
    }

    @Override
    public void cancelOrder(Long id) {

        User user = getCurrentUser();

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Order not found with id: " + id));

        validateOwnership(order, user);

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new BadRequestException(
                    "Only pending orders can be cancelled.");
        }

        // Restore Stock
        for (OrderItem item : order.getOrderItems()) {

            ProductVariant variant = item.getProductVariant();

            variant.setStock(
                    variant.getStock() + item.getQuantity()
            );

            productVariantRepository.save(variant);
        }

        order.setOrderStatus(OrderStatus.CANCELLED);

        // Offline payment was never completed
        if (order.getPaymentStatus() == PaymentStatus.PENDING) {
            order.setPaymentStatus(PaymentStatus.FAILED);
        }

        orderRepository.save(order);
    }

    @Override
    public InvoiceResponse getInvoiceView(Long id) {

        Order order = getOwnedOrder(id);

        return invoiceService.buildView(invoiceGenerationService.generateForRetailOrder(order));
    }

    @Override
    public byte[] getInvoicePdf(Long id) {

        Order order = getOwnedOrder(id);

        return invoiceService.generatePdf(invoiceGenerationService.generateForRetailOrder(order));
    }

    private Order getOwnedOrder(Long id) {

        User user = getCurrentUser();

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Order not found with id: " + id));

        validateOwnership(order, user);

        return order;
    }

    private void validateOwnership(Order order, User user) {

        // order.getUser() is null for guest orders -- a registered customer
        // must never be able to reach one via this (authenticated) path.
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {

        	throw new UnauthorizedAccessException(
        		    "You are not allowed to access this order.");
        }
    }

    private record ReservationLine(Long productVariantId, Integer quantity, BigDecimal unitPrice) {}

    private record ShippingSnapshot(
            String fullName, String phone, String phoneCountryCode,
            String addressLine1, String addressLine2, String city, String state,
            String postalCode, String country, String countryCode) {

        static ShippingSnapshot fromAddress(Address address) {

            return new ShippingSnapshot(
                    address.getFullName(), address.getPhone(), address.getPhoneCountryCode(),
                    address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getState(),
                    address.getPostalCode(), address.getCountry(), address.getCountryCode());
        }

        static ShippingSnapshot fromGuestRequest(GuestShippingAddressRequest request) {

            return new ShippingSnapshot(
                    request.getFullName(), request.getPhone(), request.getPhoneCountryCode(),
                    request.getAddressLine1(), request.getAddressLine2(), request.getCity(), request.getState(),
                    request.getPostalCode(), request.getCountry(), request.getCountryCode());
        }
    }
}
