package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stylenest.stylenest_backend.config.GstRuleProperties;
import com.stylenest.stylenest_backend.config.InvoiceSellerProperties;
import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.entity.BulkOrderItem;
import com.stylenest.stylenest_backend.entity.BulkProduct;
import com.stylenest.stylenest_backend.entity.Invoice;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.Size;
import com.stylenest.stylenest_backend.repository.InvoiceRepository;

/**
 * Covers the centralized GST engine now that manual gstRate entry has been
 * removed from the product admin: every rate must be derived automatically
 * from the CBIC apparel threshold rule (<=Rs.2500 sale value -> 5%, above
 * -> 18%), using the actual charged price, never the MRP and never a
 * stored Product/BulkProduct.gstRate value (see test7).
 */
@ExtendWith(MockitoExtension.class)
class InvoiceGenerationServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private InvoiceSellerProperties seller;
    private InvoiceGenerationServiceImpl invoiceGenerationService;

    @BeforeEach
    void setUp() {

        seller = new InvoiceSellerProperties();
        seller.setName("Style Nest Fashion");
        seller.setState("Madhya Pradesh");
        seller.setStateCode("23");

        invoiceGenerationService = new InvoiceGenerationServiceImpl(
                invoiceRepository, seller, new GstCalculationServiceImpl(new GstRuleProperties()));

        // lenient: each test only exercises the retail OR the bulk path, so
        // the other stub here is always unused in that test -- that's fine.
        lenient().when(invoiceRepository.findByRetailOrder(any())).thenReturn(Optional.empty());
        lenient().when(invoiceRepository.findByBulkOrder(any())).thenReturn(Optional.empty());
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice invoice = inv.getArgument(0);
            if (invoice.getId() == null) {
                invoice.setId(1L);
            }
            return invoice;
        });
    }

    private Order retailOrder(BigDecimal mrp, BigDecimal salePrice, String shippingState, BigDecimal staleStoredGstRate) {

        Product product = Product.builder()
                .id(1L)
                .name("Test Apparel")
                .price(mrp)
                .hsnCode("6109")
                .gstRate(staleStoredGstRate) // unused DB column -- must never affect the result
                .build();

        ProductVariant variant = ProductVariant.builder()
                .id(1L)
                .color("BLACK")
                .size(Size.M)
                .product(product)
                .build();

        OrderItem item = OrderItem.builder()
                .productVariant(variant)
                .quantity(1)
                .price(salePrice)
                .build();

        return Order.builder()
                .id(1L)
                .orderNumber("SN-TEST-1")
                .shippingFullName("Test Customer")
                .guestEmail("test@example.com")
                .shippingPhone("9998887777")
                .shippingAddressLine1("123 Test St")
                .shippingCity("Indore")
                .shippingState(shippingState)
                .shippingPostalCode("452001")
                .shippingCountry("India")
                .paymentMethod(PaymentMethod.COD)
                .orderItems(List.of(item))
                .build();
    }

    @Test
    void test1_mrp1500_sale1000_resolvesFivePercent() {

        Order order = retailOrder(new BigDecimal("1500"), new BigDecimal("1000"), "Madhya Pradesh", null);

        Invoice invoice = invoiceGenerationService.generateForRetailOrder(order);

        assertThat(invoice.getItems().get(0).getGstRate()).isEqualByComparingTo("5");
    }

    @Test
    void test2_mrp2500_sale2500_atExactThreshold_resolvesFivePercent() {

        Order order = retailOrder(new BigDecimal("2500"), new BigDecimal("2500"), "Madhya Pradesh", null);

        Invoice invoice = invoiceGenerationService.generateForRetailOrder(order);

        assertThat(invoice.getItems().get(0).getGstRate()).isEqualByComparingTo("5");
    }

    @Test
    void test3_mrp3000_sale2700_aboveThreshold_resolvesEighteenPercent() {

        Order order = retailOrder(new BigDecimal("3000"), new BigDecimal("2700"), "Madhya Pradesh", null);

        Invoice invoice = invoiceGenerationService.generateForRetailOrder(order);

        assertThat(invoice.getItems().get(0).getGstRate()).isEqualByComparingTo("18");
    }

    @Test
    void test4_mrp3000_sale2400_belowThresholdDespiteHigherMrp_resolvesFivePercent() {

        // Proves the slab is decided by the actual SALE value, never the MRP.
        Order order = retailOrder(new BigDecimal("3000"), new BigDecimal("2400"), "Madhya Pradesh", null);

        Invoice invoice = invoiceGenerationService.generateForRetailOrder(order);

        assertThat(invoice.getItems().get(0).getGstRate()).isEqualByComparingTo("5");
    }

    @Test
    void alreadyFinalizedInvoice_isNeverRecomputed_evenIfCurrentRuleWouldDifferNow() {

        // Represents an invoice generated in the past under an OLD rule
        // (e.g. before a future GST Council rate change) -- must be
        // returned exactly as stored, never regenerated against whatever
        // GstRuleProperties says today.
        Invoice existingInvoice = Invoice.builder().id(99L).invoiceNumber("INV-000099").build();
        Order order = retailOrder(new BigDecimal("1500"), new BigDecimal("1000"), "Madhya Pradesh", null);

        when(invoiceRepository.findByRetailOrder(order)).thenReturn(Optional.of(existingInvoice));

        Invoice result = invoiceGenerationService.generateForRetailOrder(order);

        assertThat(result).isSameAs(existingInvoice);
        assertThat(result.getItems()).isEmpty(); // never touched/recomputed
    }

    @Test
    void test5_interState_usesIgstOnly_neverCgstSgst() {

        Order order = retailOrder(new BigDecimal("1500"), new BigDecimal("1000"), "Maharashtra", null);

        Invoice invoice = invoiceGenerationService.generateForRetailOrder(order);

        assertThat(invoice.isInterState()).isTrue();
        assertThat(invoice.getIgstAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(invoice.getCgstAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getSgstAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void test6_intraState_usesCgstAndSgstEqually_neverIgst() {

        Order order = retailOrder(new BigDecimal("1500"), new BigDecimal("1000"), "Madhya Pradesh", null);

        Invoice invoice = invoiceGenerationService.generateForRetailOrder(order);

        assertThat(invoice.isInterState()).isFalse();
        assertThat(invoice.getCgstAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(invoice.getSgstAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(invoice.getCgstAmount()).isEqualByComparingTo(invoice.getSgstAmount());
        assertThat(invoice.getIgstAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void test7_staleStoredProductGstRate_isNeverUsed_evenWhenSetToAWrongValue() {

        // Simulates a leftover, no-longer-editable gstRate value sitting in
        // the DB column from before this change -- the invoice must still
        // compute 5% from the actual sale price, proving no override path
        // remains anywhere in the engine.
        Order order = retailOrder(new BigDecimal("1500"), new BigDecimal("1000"), "Madhya Pradesh", new BigDecimal("12"));

        Invoice invoice = invoiceGenerationService.generateForRetailOrder(order);

        assertThat(invoice.getItems().get(0).getGstRate()).isEqualByComparingTo("5");
    }

    // ---- bulk orders reuse the exact same engine -- no separate GST logic ----

    private BulkOrder bulkOrder(BigDecimal mrp, BigDecimal salePrice, String shippingState, BigDecimal staleStoredGstRate) {

        BulkProduct product = BulkProduct.builder()
                .id(1L)
                .name("Bulk Test Apparel")
                .price(mrp)
                .hsnCode("6109")
                .gstRate(staleStoredGstRate)
                .minOrderQuantity(50)
                .build();

        BulkOrderItem item = BulkOrderItem.builder()
                .bulkProduct(product)
                .productNameSnapshot(product.getName())
                .quantity(1)
                .unitPrice(salePrice)
                .build();

        return BulkOrder.builder()
                .id(1L)
                .bulkOrderNumber("BLK-TEST-1")
                .customerName("Bulk Test Customer")
                .customerEmail("bulk@example.com")
                .customerPhone("9998887777")
                .shippingAddressLine1("123 Wholesale St")
                .shippingCity("Indore")
                .shippingState(shippingState)
                .shippingPostalCode("452001")
                .shippingCountry("India")
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(item))
                .build();
    }

    @Test
    void bulkOrder_aboveThreshold_resolvesEighteenPercent_sameEngineAsRetail() {

        BulkOrder order = bulkOrder(new BigDecimal("3000"), new BigDecimal("2700"), "Madhya Pradesh", null);

        Invoice invoice = invoiceGenerationService.generateForBulkOrder(order);

        assertThat(invoice.getItems().get(0).getGstRate()).isEqualByComparingTo("18");
        assertThat(invoice.isInterState()).isFalse();
        assertThat(invoice.getCgstAmount()).isEqualByComparingTo(invoice.getSgstAmount());
    }

    @Test
    void bulkOrder_interState_usesIgst_sameEngineAsRetail() {

        BulkOrder order = bulkOrder(new BigDecimal("1500"), new BigDecimal("1000"), "Karnataka", null);

        Invoice invoice = invoiceGenerationService.generateForBulkOrder(order);

        assertThat(invoice.isInterState()).isTrue();
        assertThat(invoice.getIgstAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(invoice.getCgstAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void bulkOrder_staleStoredGstRate_isNeverUsed() {

        BulkOrder order = bulkOrder(new BigDecimal("1500"), new BigDecimal("1000"), "Madhya Pradesh", new BigDecimal("12"));

        Invoice invoice = invoiceGenerationService.generateForBulkOrder(order);

        assertThat(invoice.getItems().get(0).getGstRate()).isEqualByComparingTo("5");
    }
}
