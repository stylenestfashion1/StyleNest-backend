package com.stylenest.stylenest_backend.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

/**
 * One invoice line, fully snapshotted at generation time -- see
 * Invoice's class javadoc. mrpPerUnit/pricePerUnit/etc are all tax-EXCLUSIVE
 * figures derived from the tax-INCLUSIVE prices actually charged (see
 * InvoiceGenerationServiceImpl); lineTotal always equals what the customer
 * actually paid for this line, by construction.
 */
@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private String productName;

    // e.g. "Blue / M" for a retail variant. Null for bulk items (no variants).
    private String variantInfo;

    private String hsnCode;

    @Column(nullable = false)
    private BigDecimal mrpPerUnit;

    @Column(nullable = false)
    private Integer quantity;

    @Builder.Default
    private String unit = "Unit";

    // Tax-exclusive unit price actually charged (after any discount), i.e.
    // the "Price/Unit" column in the reference invoice.
    @Column(nullable = false)
    private BigDecimal pricePerUnit;

    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal gstRate = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal gstAmount = BigDecimal.ZERO;

    // Taxable value for the full line (pricePerUnit * quantity).
    @Column(nullable = false)
    private BigDecimal taxableAmount;

    // taxableAmount + gstAmount -- always equal to the amount actually
    // charged for this line.
    @Column(nullable = false)
    private BigDecimal lineTotal;
}
