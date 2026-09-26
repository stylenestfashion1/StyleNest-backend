package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.stylenest.stylenest_backend.enums.Currency;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    // Nullable: a guest order (no account) has no User. Registered orders
    // always have this set.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    // Guest orders only. Registered orders use user.getEmail().
    private String guestEmail;

    // Kept for admin backward-navigation to the live Address row on
    // registered orders only (null for guest orders, which have no saved
    // Address). NOT the source of truth for display/PDF/payment-gateway
    // data -- editing this live row must never change how a past order
    // looks. Use the shipping* snapshot fields below for that; they are
    // captured once, at order-creation time, and never change afterward.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = true)
    private Address address;

    // Shipping address snapshot -- captured at order-creation time for
    // EVERY order (guest and registered alike), so a later edit to a
    // saved Address (or the complete absence of one, for guests) can
    // never change how a historical order/invoice/email looks.
    private String shippingFullName;
    private String shippingPhone;
    private String shippingPhoneCountryCode;
    private String shippingAddressLine1;
    private String shippingAddressLine2;
    private String shippingCity;
    private String shippingState;
    private String shippingPostalCode;
    private String shippingCountry;
    private String shippingCountryCode;

    // Guards against sending the order-confirmation email twice (e.g. a
    // duplicate Easebuzz callback).
    @Builder.Default
    private Boolean confirmationEmailSent = false;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(nullable = false)
    private BigDecimal totalAmount;

    // The single authoritative currency this order was placed and paid in --
    // set once at order-creation time (see OrderServiceImpl), never changed
    // afterward. Nullable only because historical orders predate this column;
    // PricingBackfillRunner backfills every existing row to INR (all of
    // which were verified to actually be India/INR orders before doing so).
    // Every new order always has this set.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus orderStatus = OrderStatus.PENDING;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

   
}