package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.entity.Invoice;
import com.stylenest.stylenest_backend.entity.InvoiceItem;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.InvoiceOrderType;

/**
 * InvoiceServiceImpl is a pure renderer over an already-built Invoice
 * snapshot (see InvoiceGenerationServiceImpl for the actual GST/discount
 * calculation, which is tested separately) -- these tests construct the
 * snapshot directly and verify the PDF/JSON view faithfully reflects it,
 * never recomputes anything from live data.
 */
class InvoiceServiceImplTest {

    private final InvoiceServiceImpl invoiceService = new InvoiceServiceImpl();

    private Invoice buildInvoice(boolean guest) {

        Order.OrderBuilder orderBuilder = Order.builder().id(1L).orderNumber("SN-INV-1");

        if (!guest) {
            orderBuilder.user(User.builder().id(1L).email("customer@example.com").build());
        }

        Order order = orderBuilder.build();

        InvoiceItem item = InvoiceItem.builder()
                .productName("Rose Wrap Midi Dress")
                .variantInfo("BLACK / M")
                .hsnCode("6204")
                .mrpPerUnit(new BigDecimal("2666.67"))
                .quantity(2)
                .unit("Unit")
                .pricePerUnit(new BigDecimal("2666.67"))
                .discountAmount(BigDecimal.ZERO)
                .discountPercent(BigDecimal.ZERO)
                .gstRate(new BigDecimal("5"))
                .gstAmount(new BigDecimal("266.66"))
                .taxableAmount(new BigDecimal("5333.34"))
                .lineTotal(new BigDecimal("5600.00"))
                .build();

        Invoice invoice = Invoice.builder()
                .id(1L)
                .invoiceNumber("INV-000001")
                .invoiceDate(LocalDateTime.now())
                .orderType(InvoiceOrderType.RETAIL)
                .retailOrder(order)
                .sellerName("Style Nest Fashion")
                .sellerAddress("175-B, Amrit Palace, Nipania, Indore, Madhya Pradesh - 452010")
                .sellerPhone("6269933231")
                .sellerEmail("stylenestfashion1@gmail.com")
                .sellerState("Madhya Pradesh")
                .sellerGstin("23ABUCS8160R1ZA")
                .sellerCin("U14101MP2026PTC086429")
                .customerName(guest ? "Guest Customer" : "Registered Customer")
                .customerEmail(guest ? "guest@example.com" : "customer@example.com")
                .billingAddressLine1("123 Main St")
                .billingCity("Testville")
                .billingState("Madhya Pradesh")
                .billingPostalCode("123456")
                .billingCountry("India")
                .taxableAmount(new BigDecimal("5333.34"))
                .totalDiscount(BigDecimal.ZERO)
                .cgstAmount(new BigDecimal("133.33"))
                .sgstAmount(new BigDecimal("133.33"))
                .igstAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .roundOff(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("5600.00"))
                .amountInWords("Five Thousand Six Hundred Rupees only")
                .paymentMethod("COD")
                .paymentStatus("PENDING")
                .build();

        item.setInvoice(invoice);
        invoice.getItems().add(item);

        return invoice;
    }

    @Test
    void generatePdf_returnsNonEmptyValidPdfBytes() {

        byte[] pdf = invoiceService.generatePdf(buildInvoice(false));

        assertThat(pdf).isNotEmpty();
        // PDF files start with the "%PDF" magic bytes.
        String header = new String(pdf, 0, 4, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF");
    }

    @Test
    void generatePdf_guestOrder_alsoProducesValidPdf() {

        byte[] pdf = invoiceService.generatePdf(buildInvoice(true));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void buildView_reflectsExactlyWhatWasSnapshotted_neverRecomputed() {

        Invoice invoice = buildInvoice(false);

        InvoiceResponse view = invoiceService.buildView(invoice);

        // The whole point of the snapshot architecture: the view must equal
        // whatever was stored on the Invoice/InvoiceItem at generation time,
        // not something derived fresh from live product/order data.
        assertThat(view.getGrandTotal()).isEqualByComparingTo("5600.00");
        assertThat(view.getItems().get(0).getPricePerUnit()).isEqualByComparingTo("2666.67");
        assertThat(view.getItems().get(0).getLineTotal()).isEqualByComparingTo("5600.00");
    }

    @Test
    void buildView_guestOrder_usesGuestEmailAndSnapshotAddress() {

        InvoiceResponse view = invoiceService.buildView(buildInvoice(true));

        assertThat(view.getIsGuest()).isTrue();
        assertThat(view.getCustomerEmail()).isEqualTo("guest@example.com");
        assertThat(view.getCustomerName()).isEqualTo("Guest Customer");
        assertThat(view.getBillingCity()).isEqualTo("Testville");
    }

    @Test
    void buildView_registeredOrder_isGuestFalse() {

        InvoiceResponse view = invoiceService.buildView(buildInvoice(false));

        assertThat(view.getIsGuest()).isFalse();
        assertThat(view.getCustomerEmail()).isEqualTo("customer@example.com");
    }

    @Test
    void buildView_invoiceNumberComesFromTheSnapshot() {

        InvoiceResponse view = invoiceService.buildView(buildInvoice(false));

        assertThat(view.getInvoiceNumber()).isEqualTo("INV-000001");
        assertThat(view.getOrderNumber()).isEqualTo("SN-INV-1");
    }

    @Test
    void buildView_intraStateOrder_taxBreakupSplitsCgstAndSgst() {

        InvoiceResponse view = invoiceService.buildView(buildInvoice(false));

        assertThat(view.getInterState()).isFalse();
        assertThat(view.getTaxBreakup()).extracting("taxType").contains("CGST", "SGST");
    }
}
