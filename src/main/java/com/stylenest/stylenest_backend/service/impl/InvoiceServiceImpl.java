package com.stylenest.stylenest_backend.service.impl;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.stylenest.stylenest_backend.dto.invoice.InvoiceItemResponse;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.exception.InvoiceGenerationException;
import com.stylenest.stylenest_backend.service.InvoiceService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Override
    public byte[] generateInvoicePdf(Order order) {

        try {

            Document document = new Document();
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            document.add(new Paragraph("StyleNest", titleFont));

            Paragraph invoiceHeading = new Paragraph("Invoice #" + order.getOrderNumber(), headingFont);
            invoiceHeading.setSpacingBefore(12);
            document.add(invoiceHeading);

            document.add(new Paragraph(
                    "Invoice Date: " + (order.getCreatedAt() == null ? "" : order.getCreatedAt().format(DATE_FORMAT)),
                    normalFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Bill To / Ship To", headingFont));
            document.add(new Paragraph(nullToEmpty(order.getShippingFullName()), normalFont));

            String addressLine = nullToEmpty(order.getShippingAddressLine1())
                    + (isBlank(order.getShippingAddressLine2()) ? "" : ", " + order.getShippingAddressLine2());
            document.add(new Paragraph(addressLine, normalFont));

            document.add(new Paragraph(
                    nullToEmpty(order.getShippingCity()) + ", " + nullToEmpty(order.getShippingState())
                            + " - " + nullToEmpty(order.getShippingPostalCode()),
                    normalFont));
            document.add(new Paragraph(nullToEmpty(order.getShippingCountry()), normalFont));
            document.add(new Paragraph(
                    "Phone: " + nullToEmpty(order.getShippingPhoneCountryCode()) + " " + nullToEmpty(order.getShippingPhone()),
                    normalFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 3f, 2f, 1f, 1.5f, 1.5f });

            addHeaderCell(table, "Item", boldFont);
            addHeaderCell(table, "Color / Size", boldFont);
            addHeaderCell(table, "Qty", boldFont);
            addHeaderCell(table, "Price", boldFont);
            addHeaderCell(table, "Subtotal", boldFont);

            for (OrderItem item : order.getOrderItems()) {

                BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

                table.addCell(new Phrase(item.getProductVariant().getProduct().getName(), normalFont));
                table.addCell(new Phrase(
                        item.getProductVariant().getColor() + " / " + item.getProductVariant().getSize(), normalFont));
                table.addCell(new Phrase(String.valueOf(item.getQuantity()), normalFont));
                table.addCell(new Phrase(item.getPrice().toPlainString(), normalFont));
                table.addCell(new Phrase(subtotal.toPlainString(), normalFont));
            }

            document.add(table);
            document.add(new Paragraph(" "));

            // Never recomputed -- this is the same totalAmount already
            // persisted on the order at purchase time.
            Paragraph total = new Paragraph("Total: Rs. " + order.getTotalAmount().toPlainString(), headingFont);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Payment Method: " + order.getPaymentMethod(), normalFont));
            document.add(new Paragraph("Payment Status: " + order.getPaymentStatus(), normalFont));

            document.close();

            return out.toByteArray();

        } catch (Exception ex) {

            throw new InvoiceGenerationException("Could not generate the invoice. Please try again.", ex);
        }
    }

    @Override
    public InvoiceResponse buildInvoiceView(Order order) {

        var items = order.getOrderItems().stream()
                .map(this::toItemResponse)
                .toList();

        boolean isGuest = order.getUser() == null;

        return InvoiceResponse.builder()
                .invoiceNumber(order.getOrderNumber())
                .invoiceDate(order.getCreatedAt())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .isGuest(isGuest)
                .customerName(order.getShippingFullName())
                .customerEmail(isGuest ? order.getGuestEmail() : order.getUser().getEmail())
                .customerPhone(order.getShippingPhone())
                .shippingAddressLine1(order.getShippingAddressLine1())
                .shippingAddressLine2(order.getShippingAddressLine2())
                .shippingCity(order.getShippingCity())
                .shippingState(order.getShippingState())
                .shippingPostalCode(order.getShippingPostalCode())
                .shippingCountry(order.getShippingCountry())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .build();
    }

    private InvoiceItemResponse toItemResponse(OrderItem item) {

        BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        return InvoiceItemResponse.builder()
                .productName(item.getProductVariant().getProduct().getName())
                .color(item.getProductVariant().getColor().name())
                .size(item.getProductVariant().getSize().name())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(subtotal)
                .build();
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {

        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        table.addCell(cell);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
