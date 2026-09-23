package com.stylenest.stylenest_backend.service.impl;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceLineItemResponse;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceTaxBreakupResponse;
import com.stylenest.stylenest_backend.entity.Invoice;
import com.stylenest.stylenest_backend.entity.InvoiceItem;
import com.stylenest.stylenest_backend.exception.InvoiceGenerationException;
import com.stylenest.stylenest_backend.service.InvoiceService;

/**
 * Renders the shared Invoice entity (retail or bulk -- indistinguishable
 * from here on) as a JSON view or a real PDF document, in the layout
 * structure of the business's reference Vyapar invoice: seller header,
 * "Tax Invoice" title, Bill To / Invoice Details, item table, tax-type
 * breakdown + amounts summary, amount in words, signature block.
 */
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final Color BRAND_PURPLE = new Color(124, 108, 214);
    private static final Color LIGHT_GREY = new Color(245, 245, 247);

    @Override
    public byte[] generatePdf(Invoice invoice) {

        try {

            Document document = new Document(PageSize.A4, 36, 36, 48, 48);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font tableCellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            // ---- Seller header ----
            Paragraph name = new Paragraph(nullToEmpty(invoice.getSellerName()), titleFont);
            name.setAlignment(Element.ALIGN_CENTER);
            document.add(name);

            StringBuilder contactLine = new StringBuilder();
            if (invoice.getSellerAddress() != null) contactLine.append(invoice.getSellerAddress());
            if (invoice.getSellerPhone() != null) contactLine.append(", Ph. no.: ").append(invoice.getSellerPhone());
            if (invoice.getSellerEmail() != null) contactLine.append(" Email: ").append(invoice.getSellerEmail());
            Paragraph contact = new Paragraph(contactLine.toString(), subFont);
            contact.setAlignment(Element.ALIGN_CENTER);
            document.add(contact);

            if (invoice.getSellerState() != null) {
                Paragraph state = new Paragraph("State: " + invoice.getSellerState(), subFont);
                state.setAlignment(Element.ALIGN_CENTER);
                document.add(state);
            }

            StringBuilder regLine = new StringBuilder();
            if (invoice.getSellerGstin() != null) regLine.append("GSTIN: ").append(invoice.getSellerGstin());
            if (invoice.getSellerCin() != null) {
                if (regLine.length() > 0) regLine.append("   ");
                regLine.append("CIN No.: ").append(invoice.getSellerCin());
            }
            if (regLine.length() > 0) {
                Paragraph reg = new Paragraph(regLine.toString(), subFont);
                reg.setAlignment(Element.ALIGN_CENTER);
                reg.setSpacingAfter(8);
                document.add(reg);
            }

            document.add(ruleTable());

            // ---- Title ----
            PdfPTable titleBar = new PdfPTable(1);
            titleBar.setWidthPercentage(100);
            titleBar.setSpacingBefore(6);
            titleBar.setSpacingAfter(10);
            PdfPCell titleCell = new PdfPCell(new Phrase("Tax Invoice", sectionFont));
            titleCell.setBackgroundColor(BRAND_PURPLE);
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setPadding(6);
            titleCell.setBorder(0);
            titleBar.addCell(titleCell);
            document.add(titleBar);

            // ---- Bill To / Invoice Details ----
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[] { 1.4f, 1f });

            PdfPCell billTo = new PdfPCell();
            billTo.setBorder(0);
            billTo.addElement(new Paragraph("Bill To", headingFont));
            billTo.addElement(new Paragraph(nullToEmpty(invoice.getCustomerName()), boldFont));
            if (!isBlank(invoice.getBillingAddressLine1())) {
                billTo.addElement(new Paragraph(addressLine(invoice), normalFont));
            }
            if (!isBlank(invoice.getCustomerPhone())) {
                billTo.addElement(new Paragraph("Contact No.: " + invoice.getCustomerPhone(), normalFont));
            }
            if (!isBlank(invoice.getCustomerGstin())) {
                billTo.addElement(new Paragraph("GSTIN: " + invoice.getCustomerGstin(), normalFont));
            }
            header.addCell(billTo);

            PdfPCell details = new PdfPCell();
            details.setBorder(0);
            details.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph detailsHeading = new Paragraph("Invoice Details", headingFont);
            detailsHeading.setAlignment(Element.ALIGN_RIGHT);
            details.addElement(detailsHeading);
            details.addElement(rightPara("Invoice No.: " + nullToEmpty(invoice.getInvoiceNumber()), normalFont));
            details.addElement(rightPara(
                    "Date: " + (invoice.getInvoiceDate() == null ? "" : invoice.getInvoiceDate().format(DATE_FORMAT)),
                    normalFont));
            details.addElement(rightPara("Order No.: " + nullToEmpty(orderReference(invoice)), normalFont));
            header.addCell(details);

            header.setSpacingAfter(10);
            document.add(header);

            // ---- Item table ----
            PdfPTable table = new PdfPTable(10);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 0.4f, 2f, 0.8f, 0.8f, 0.6f, 0.6f, 0.8f, 1f, 1f, 1f });

            addHeaderCell(table, "#", tableHeaderFont);
            addHeaderCell(table, "Item name", tableHeaderFont);
            addHeaderCell(table, "HSN/SAC", tableHeaderFont);
            addHeaderCell(table, "MRP", tableHeaderFont);
            addHeaderCell(table, "Qty", tableHeaderFont);
            addHeaderCell(table, "Unit", tableHeaderFont);
            addHeaderCell(table, "Price/Unit", tableHeaderFont);
            addHeaderCell(table, "Discount", tableHeaderFont);
            addHeaderCell(table, "GST", tableHeaderFont);
            addHeaderCell(table, "Amount", tableHeaderFont);

            int sr = 1;
            for (InvoiceItem item : invoice.getItems()) {

                addCell(table, String.valueOf(sr++), tableCellFont, Element.ALIGN_CENTER);

                String itemName = item.getProductName() + (item.getVariantInfo() != null ? "\n" + item.getVariantInfo() : "");
                addCell(table, itemName, tableCellFont, Element.ALIGN_LEFT);

                addCell(table, nullToEmpty(item.getHsnCode()), tableCellFont, Element.ALIGN_CENTER);
                addCell(table, money(item.getMrpPerUnit()), tableCellFont, Element.ALIGN_RIGHT);
                addCell(table, String.valueOf(item.getQuantity()), tableCellFont, Element.ALIGN_CENTER);
                addCell(table, item.getUnit(), tableCellFont, Element.ALIGN_CENTER);
                addCell(table, money(item.getPricePerUnit()), tableCellFont, Element.ALIGN_RIGHT);
                addCell(table, money(item.getDiscountAmount()) + "\n(" + item.getDiscountPercent() + "%)", tableCellFont, Element.ALIGN_RIGHT);
                addCell(table, money(item.getGstAmount()) + "\n(" + item.getGstRate() + "%)", tableCellFont, Element.ALIGN_RIGHT);
                addCell(table, money(item.getLineTotal()), tableCellFont, Element.ALIGN_RIGHT);
            }

            document.add(table);

            // ---- Amount table (right) ----
            PdfPTable amounts = new PdfPTable(2);
            amounts.setWidthPercentage(55);
            amounts.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amounts.setSpacingBefore(12);
            amounts.setWidths(new float[] { 1.4f, 1f });

            amountRow(amounts, "Taxable Amount", money(invoice.getTaxableAmount()), normalFont, false);
            if (invoice.getTotalDiscount() != null && invoice.getTotalDiscount().signum() > 0) {
                amountRow(amounts, "Total Discount", money(invoice.getTotalDiscount()), normalFont, false);
            }
            if (invoice.isInterState()) {
                amountRow(amounts, "IGST", money(invoice.getIgstAmount()), normalFont, false);
            } else {
                amountRow(amounts, "CGST", money(invoice.getCgstAmount()), normalFont, false);
                amountRow(amounts, "SGST", money(invoice.getSgstAmount()), normalFont, false);
            }
            if (invoice.getShippingCharge() != null && invoice.getShippingCharge().signum() > 0) {
                amountRow(amounts, "Shipping", money(invoice.getShippingCharge()), normalFont, false);
            }
            amountRow(amounts, "Round Off", money(invoice.getRoundOff()), normalFont, false);
            amountRow(amounts, "Grand Total", money(invoice.getGrandTotal()), boldFont, true);

            boolean paid = "PAID".equalsIgnoreCase(invoice.getPaymentStatus());
            BigDecimal received = paid ? invoice.getGrandTotal() : BigDecimal.ZERO;
            BigDecimal balance = invoice.getGrandTotal().subtract(received);

            amountRow(amounts, "Payment Status", nullToEmpty(invoice.getPaymentStatus()), normalFont, false);
            amountRow(amounts, "Received", money(received), normalFont, false);
            amountRow(amounts, "Balance", money(balance), normalFont, false);

            document.add(amounts);

            // ---- Tax type breakdown (left), grouped by rate ----
            List<InvoiceTaxBreakupResponse> breakup = buildTaxBreakup(invoice);

            PdfPTable taxTable = new PdfPTable(4);
            taxTable.setWidthPercentage(50);
            taxTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            taxTable.setSpacingBefore(12);
            taxTable.setWidths(new float[] { 0.8f, 1f, 0.6f, 1f });

            addHeaderCell(taxTable, "Tax Type", tableHeaderFont);
            addHeaderCell(taxTable, "Taxable Amount", tableHeaderFont);
            addHeaderCell(taxTable, "Rate", tableHeaderFont);
            addHeaderCell(taxTable, "Tax Amount", tableHeaderFont);

            for (InvoiceTaxBreakupResponse row : breakup) {
                addCell(taxTable, row.getTaxType(), tableCellFont, Element.ALIGN_LEFT);
                addCell(taxTable, money(row.getTaxableAmount()), tableCellFont, Element.ALIGN_RIGHT);
                addCell(taxTable, row.getRate() + "%", tableCellFont, Element.ALIGN_CENTER);
                addCell(taxTable, money(row.getTaxAmount()), tableCellFont, Element.ALIGN_RIGHT);
            }

            document.add(taxTable);

            // ---- Amount in words ----
            Paragraph wordsHeading = new Paragraph("Invoice Amount In Words", headingFont);
            wordsHeading.setSpacingBefore(14);
            document.add(wordsHeading);
            document.add(new Paragraph(nullToEmpty(invoice.getAmountInWords()), normalFont));

            document.close();

            return out.toByteArray();

        } catch (Exception ex) {
            throw new InvoiceGenerationException("Could not generate the invoice. Please try again.", ex);
        }
    }

    @Override
    public InvoiceResponse buildView(Invoice invoice) {

        var items = invoice.getItems().stream()
                .map(this::toLineItemResponse)
                .toList();

        boolean paid = "PAID".equalsIgnoreCase(invoice.getPaymentStatus());
        BigDecimal amountReceived = paid ? invoice.getGrandTotal() : BigDecimal.ZERO;
        BigDecimal balanceDue = invoice.getGrandTotal().subtract(amountReceived);

        return InvoiceResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceDate(invoice.getInvoiceDate())
                .orderType(invoice.getOrderType().name())
                .orderId(invoice.getRetailOrder() != null ? invoice.getRetailOrder().getId()
                        : invoice.getBulkOrder() != null ? invoice.getBulkOrder().getId() : null)
                .orderNumber(orderReference(invoice))
                .isGuest(invoice.getRetailOrder() != null && invoice.getRetailOrder().getUser() == null)
                .sellerName(invoice.getSellerName())
                .sellerAddress(invoice.getSellerAddress())
                .sellerPhone(invoice.getSellerPhone())
                .sellerEmail(invoice.getSellerEmail())
                .sellerState(invoice.getSellerState())
                .sellerGstin(invoice.getSellerGstin())
                .sellerCin(invoice.getSellerCin())
                .customerName(invoice.getCustomerName())
                .customerEmail(invoice.getCustomerEmail())
                .customerPhone(invoice.getCustomerPhone())
                .customerGstin(invoice.getCustomerGstin())
                .billingAddressLine1(invoice.getBillingAddressLine1())
                .billingAddressLine2(invoice.getBillingAddressLine2())
                .billingCity(invoice.getBillingCity())
                .billingState(invoice.getBillingState())
                .billingPostalCode(invoice.getBillingPostalCode())
                .billingCountry(invoice.getBillingCountry())
                .items(items)
                .taxableAmount(invoice.getTaxableAmount())
                .totalDiscount(invoice.getTotalDiscount())
                .cgstAmount(invoice.getCgstAmount())
                .sgstAmount(invoice.getSgstAmount())
                .igstAmount(invoice.getIgstAmount())
                .shippingCharge(invoice.getShippingCharge())
                .roundOff(invoice.getRoundOff())
                .grandTotal(invoice.getGrandTotal())
                .interState(invoice.isInterState())
                .taxBreakup(buildTaxBreakup(invoice))
                .amountInWords(invoice.getAmountInWords())
                .paymentMethod(invoice.getPaymentMethod())
                .paymentStatus(invoice.getPaymentStatus())
                .amountReceived(amountReceived)
                .balanceDue(balanceDue)
                .build();
    }

    /**
     * Groups line items by GST rate and produces one CGST+SGST row pair
     * (or one IGST row) per distinct rate -- so an order mixing a <=Rs.2500
     * item (5%) and a >Rs.2500 item (18%) shows a correct, separate tax
     * breakup per rate rather than one blended figure.
     */
    private List<InvoiceTaxBreakupResponse> buildTaxBreakup(Invoice invoice) {

        Map<BigDecimal, BigDecimal> taxableByRate = new LinkedHashMap<>();

        for (InvoiceItem item : invoice.getItems()) {
            taxableByRate.merge(item.getGstRate(), item.getTaxableAmount(), BigDecimal::add);
        }

        List<InvoiceTaxBreakupResponse> rows = new ArrayList<>();
        boolean interState = invoice.isInterState();

        for (Map.Entry<BigDecimal, BigDecimal> entry : taxableByRate.entrySet()) {

            BigDecimal rate = entry.getKey();
            BigDecimal groupTaxable = entry.getValue();

            if (interState) {
                rows.add(InvoiceTaxBreakupResponse.builder()
                        .taxType("IGST")
                        .rate(rate)
                        .taxableAmount(groupTaxable)
                        .taxAmount(taxAt(groupTaxable, rate))
                        .build());
            } else {
                BigDecimal halfRate = rate.divide(BigDecimal.valueOf(2));
                BigDecimal halfTax = taxAt(groupTaxable, halfRate);

                rows.add(InvoiceTaxBreakupResponse.builder()
                        .taxType("SGST").rate(halfRate).taxableAmount(groupTaxable).taxAmount(halfTax).build());
                rows.add(InvoiceTaxBreakupResponse.builder()
                        .taxType("CGST").rate(halfRate).taxableAmount(groupTaxable).taxAmount(halfTax).build());
            }
        }

        return rows;
    }

    private BigDecimal taxAt(BigDecimal taxableAmount, BigDecimal ratePercent) {

        return taxableAmount.multiply(ratePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private InvoiceLineItemResponse toLineItemResponse(InvoiceItem item) {

        return InvoiceLineItemResponse.builder()
                .productName(item.getProductName())
                .variantInfo(item.getVariantInfo())
                .hsnCode(item.getHsnCode())
                .mrpPerUnit(item.getMrpPerUnit())
                .quantity(item.getQuantity())
                .unit(item.getUnit())
                .pricePerUnit(item.getPricePerUnit())
                .discountAmount(item.getDiscountAmount())
                .discountPercent(item.getDiscountPercent())
                .gstRate(item.getGstRate())
                .gstAmount(item.getGstAmount())
                .taxableAmount(item.getTaxableAmount())
                .lineTotal(item.getLineTotal())
                .build();
    }

    private String orderReference(Invoice invoice) {

        if (invoice.getRetailOrder() != null) return invoice.getRetailOrder().getOrderNumber();
        if (invoice.getBulkOrder() != null) return invoice.getBulkOrder().getBulkOrderNumber();
        return null;
    }

    private String addressLine(Invoice invoice) {

        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(invoice.getBillingAddressLine1()));
        if (!isBlank(invoice.getBillingAddressLine2())) sb.append(", ").append(invoice.getBillingAddressLine2());
        sb.append(", ").append(nullToEmpty(invoice.getBillingCity()));
        sb.append(", ").append(nullToEmpty(invoice.getBillingState()));
        if (!isBlank(invoice.getBillingPostalCode())) sb.append(" - ").append(invoice.getBillingPostalCode());
        return sb.toString();
    }

    private PdfPTable ruleTable() {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.BOTTOM);
        cell.setBorderColor(BRAND_PURPLE);
        cell.setBorderWidth(1.5f);
        cell.setFixedHeight(1f);
        rule.addCell(cell);
        return rule;
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(BRAND_PURPLE);
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private void amountRow(PdfPTable table, String label, String value, Font font, boolean emphasize) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(emphasize ? PdfPCell.TOP : 0);
        labelCell.setPadding(3);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBorder(emphasize ? PdfPCell.TOP : 0);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(3);
        table.addCell(valueCell);
    }

    private Paragraph rightPara(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private String money(java.math.BigDecimal value) {
        return value == null ? "0.00" : "Rs. " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
