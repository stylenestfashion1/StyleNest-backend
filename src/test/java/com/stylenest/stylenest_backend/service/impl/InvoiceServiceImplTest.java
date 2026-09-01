package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.enums.Color;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.enums.Size;

class InvoiceServiceImplTest {

    private final InvoiceServiceImpl invoiceService = new InvoiceServiceImpl();

    private Order buildOrder(boolean guest) {

        Product product = Product.builder().id(1L).name("Rose Wrap Midi Dress").price(new BigDecimal("2799.00")).build();

        ProductVariant variant = ProductVariant.builder()
                .id(1L).product(product).color(Color.BLACK).size(Size.M).stock(5).build();

        OrderItem item = OrderItem.builder()
                .productVariant(variant).quantity(2).price(new BigDecimal("2799.00")).build();

        Order.OrderBuilder builder = Order.builder()
                .id(1L)
                .orderNumber("F21-INV-1")
                .totalAmount(new BigDecimal("5598.00"))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .shippingFullName(guest ? "Guest Customer" : "Registered Customer")
                .shippingAddressLine1("123 Main St")
                .shippingCity("Testville")
                .shippingState("TS")
                .shippingPostalCode("123456")
                .shippingCountry("India")
                .shippingPhone("9999999999")
                .orderItems(List.of(item));

        if (guest) {
            builder.guestEmail("guest@example.com");
        } else {
            builder.user(User.builder().id(1L).email("customer@example.com").build());
        }

        return builder.build();
    }

    @Test
    void generateInvoicePdf_returnsNonEmptyValidPdfBytes() {

        byte[] pdf = invoiceService.generateInvoicePdf(buildOrder(false));

        assertThat(pdf).isNotEmpty();
        // PDF files start with the "%PDF" magic bytes.
        String header = new String(pdf, 0, 4, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF");
    }

    @Test
    void generateInvoicePdf_guestOrder_alsoProducesValidPdf() {

        byte[] pdf = invoiceService.generateInvoicePdf(buildOrder(true));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void generateInvoicePdf_totalNotRecomputed_reflectsOrderAtPurchaseTimeNotLiveProductPrice() {

        Order order = buildOrder(false);

        // Simulate the product's live price changing after the order was
        // placed -- the invoice total must still be the order's own
        // totalAmount (5598.00), never recomputed from this new price.
        order.getOrderItems().get(0).getProductVariant().getProduct().setPrice(new BigDecimal("9999.00"));

        InvoiceResponse view = invoiceService.buildInvoiceView(order);

        assertThat(view.getTotalAmount()).isEqualByComparingTo("5598.00");
        assertThat(view.getItems().get(0).getPrice()).isEqualByComparingTo("2799.00"); // the order-item's own stored price
    }

    @Test
    void buildInvoiceView_guestOrder_usesGuestEmailAndSnapshotAddress() {

        InvoiceResponse view = invoiceService.buildInvoiceView(buildOrder(true));

        assertThat(view.getIsGuest()).isTrue();
        assertThat(view.getCustomerEmail()).isEqualTo("guest@example.com");
        assertThat(view.getCustomerName()).isEqualTo("Guest Customer");
        assertThat(view.getShippingCity()).isEqualTo("Testville");
    }

    @Test
    void buildInvoiceView_registeredOrder_usesUserEmail() {

        InvoiceResponse view = invoiceService.buildInvoiceView(buildOrder(false));

        assertThat(view.getIsGuest()).isFalse();
        assertThat(view.getCustomerEmail()).isEqualTo("customer@example.com");
    }

    @Test
    void buildInvoiceView_invoiceNumberIsOrderNumber() {

        InvoiceResponse view = invoiceService.buildInvoiceView(buildOrder(false));

        assertThat(view.getInvoiceNumber()).isEqualTo("F21-INV-1");
    }
}
