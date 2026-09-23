package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.stylenest.stylenest_backend.enums.InvoiceOrderType;

import jakarta.persistence.*;
import lombok.*;

/**
 * One shared invoice model for both retail Orders and BulkOrders --
 * exactly one of {@link #retailOrder}/{@link #bulkOrder} is set, per
 * {@link #orderType}. Every field here is a snapshot taken at the moment
 * the order was finalized (see InvoiceGenerationServiceImpl): seller
 * details, customer details, and all money figures. Nothing on this
 * entity is ever recomputed from live product/business data after
 * creation -- a later price, GST rate, or business-address change must
 * never alter an already-issued invoice.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String invoiceNumber;

    @Column(nullable = false)
    private LocalDateTime invoiceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceOrderType orderType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retail_order_id")
    private Order retailOrder;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulk_order_id")
    private BulkOrder bulkOrder;

    // ---- Seller snapshot (InvoiceSellerProperties at generation time) ----
    private String sellerName;
    @Column(columnDefinition = "TEXT")
    private String sellerAddress;
    private String sellerPhone;
    private String sellerEmail;
    private String sellerState;
    private String sellerStateCode;
    private String sellerGstin;
    private String sellerCin;

    // ---- Customer/billing snapshot ----
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String customerGstin;
    private String billingAddressLine1;
    private String billingAddressLine2;
    private String billingCity;
    private String billingState;
    private String billingPostalCode;
    private String billingCountry;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    // ---- Totals (all derived from already-charged amounts -- see
    // InvoiceGenerationServiceImpl; never an additional charge on top of
    // what the customer actually paid) ----
    @Column(nullable = false)
    private BigDecimal taxableAmount;

    @Builder.Default
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal shippingCharge = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal roundOff = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal grandTotal;

    @Column(columnDefinition = "TEXT")
    private String amountInWords;

    // Kept as plain strings (not the retail PaymentMethod/PaymentStatus
    // enums) so this one entity stays decoupled from either order type's
    // specific enum choices.
    private String paymentMethod;
    private String paymentStatus;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public boolean isInterState() {
        return igstAmount != null && igstAmount.signum() > 0;
    }
}
