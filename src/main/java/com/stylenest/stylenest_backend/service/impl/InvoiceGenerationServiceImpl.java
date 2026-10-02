package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.config.InvoiceSellerProperties;
import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.entity.BulkOrderItem;
import com.stylenest.stylenest_backend.entity.Invoice;
import com.stylenest.stylenest_backend.entity.InvoiceItem;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.entity.OrderItem;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.enums.InvoiceOrderType;
import com.stylenest.stylenest_backend.repository.InvoiceRepository;
import com.stylenest.stylenest_backend.service.GstCalculationService;
import com.stylenest.stylenest_backend.service.InvoiceGenerationService;
import com.stylenest.stylenest_backend.util.NumberToWordsUtil;

import lombok.RequiredArgsConstructor;

/**
 * The one shared GST-invoice engine for both retail Orders and BulkOrders.
 *
 * Core principle: every product price in this system has always been
 * tax-INCLUSIVE (there has never been a separate "+GST at checkout" step,
 * and the customer paid exactly order.totalAmount / bulkOrder.totalAmount).
 * So GST here is a DECOMPOSITION of the already-charged amount, never an
 * addition to it -- see decomposeLine(). This guarantees invoice totals
 * always reconcile exactly with what was actually charged, regardless of
 * whether a product's GST rate is configured correctly, missing, or wrong:
 * a missing rate (null -> treated as 0%) just means the full charged
 * amount is reported as taxable with no tax split, never a silently wrong
 * total.
 *
 * GST RATE is never manually entered anywhere in the system (no admin
 * override field exists on Product/BulkProduct) -- it is ALWAYS resolved
 * via the injected GstCalculationService, the one centralized rule engine
 * shared by every flow in the app (see GstRuleProperties for the actual
 * configured threshold/rates, which an authorized developer can change in
 * one place if the GST Council revises them -- every new order/invoice
 * picks up the change automatically). The "sale value" fed into that
 * service is the actual tax-inclusive unit price charged to the customer
 * at order time (never the MRP), so the same product can legitimately
 * invoice at different rates depending on the discount applied.
 *
 * HSN CODE is never invented -- if a product has none configured, the
 * invoice simply shows it blank (see InvoiceLineItemResponse/PDF), same as
 * before.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceGenerationServiceImpl implements InvoiceGenerationService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceGenerationServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSellerProperties seller;
    private final GstCalculationService gstCalculationService;

    @Override
    public Invoice generateForRetailOrder(Order order) {

        return invoiceRepository.findByRetailOrder(order).orElseGet(() -> {

            List<LineInput> lines = order.getOrderItems().stream()
                    .map(this::toLineInput)
                    .toList();

            boolean interState = isInterState(order.getShippingState());

            String customerName = order.getUser() != null
                    ? order.getUser().getFullName()
                    : order.getShippingFullName();

            String customerEmail = order.getUser() != null
                    ? order.getUser().getEmail()
                    : order.getGuestEmail();

            BigDecimal shippingFee = order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO;

            Invoice invoice = buildInvoice(
                    InvoiceOrderType.RETAIL,
                    customerName,
                    customerEmail,
                    order.getShippingPhone(),
                    order.getShippingAddressLine1(),
                    order.getShippingAddressLine2(),
                    order.getShippingCity(),
                    order.getShippingState(),
                    order.getShippingPostalCode(),
                    order.getShippingCountry(),
                    lines,
                    interState,
                    order.getPaymentMethod().name(),
                    order.getPaymentStatus().name(),
                    shippingFee);

            invoice.setRetailOrder(order);

            return persistWithInvoiceNumber(invoice);
        });
    }

    @Override
    public Invoice generateForBulkOrder(BulkOrder bulkOrder) {

        return invoiceRepository.findByBulkOrder(bulkOrder).orElseGet(() -> {

            List<LineInput> lines = bulkOrder.getItems().stream()
                    .map(this::toLineInput)
                    .toList();

            boolean interState = isInterState(bulkOrder.getShippingState());

            Invoice invoice = buildInvoice(
                    InvoiceOrderType.BULK,
                    bulkOrder.getCustomerName(),
                    bulkOrder.getCustomerEmail(),
                    bulkOrder.getCustomerPhone(),
                    bulkOrder.getShippingAddressLine1(),
                    bulkOrder.getShippingAddressLine2(),
                    bulkOrder.getShippingCity(),
                    bulkOrder.getShippingState(),
                    bulkOrder.getShippingPostalCode(),
                    bulkOrder.getShippingCountry(),
                    lines,
                    interState,
                    bulkOrder.getPaymentMethod().name(),
                    bulkOrder.getPaymentStatus().name(),
                    BigDecimal.ZERO);

            invoice.setBulkOrder(bulkOrder);

            return persistWithInvoiceNumber(invoice);
        });
    }

    // ---- shared line decomposition ----

    /** One product line's raw inputs, uniform across retail/bulk before GST decomposition. */
    private record LineInput(
            String productName, String variantInfo, String hsnCode,
            BigDecimal mrpInclusive, Integer quantity, BigDecimal chargedUnitPriceInclusive) {}

    private LineInput toLineInput(OrderItem item) {

        ProductVariant variant = item.getProductVariant();

        String variantInfo = variant.getColor() + " / " + variant.getSize().getLabel();

        return new LineInput(
                variant.getProduct().getName(),
                variantInfo,
                variant.getProduct().getHsnCode(),
                variant.getProduct().getPrice(),
                item.getQuantity(),
                item.getPrice());
    }

    private LineInput toLineInput(BulkOrderItem item) {

        return new LineInput(
                item.getProductNameSnapshot(),
                null,
                item.getBulkProduct().getHsnCode(),
                item.getBulkProduct().getPrice(),
                item.getQuantity(),
                item.getUnitPrice());
    }

    /**
     * Strips tax out of an already tax-inclusive charged price to get the
     * taxable value, then re-derives the tax amount from that -- so
     * taxableAmount + gstAmount always reconstructs exactly the amount
     * that was actually charged (order.totalAmount), independent of the
     * resolved GST rate.
     */
    private InvoiceItem decomposeLine(LineInput line) {

        BigDecimal effectiveRate = gstCalculationService.resolveApparelGstRate(line.chargedUnitPriceInclusive());

        BigDecimal qty = BigDecimal.valueOf(line.quantity());
        BigDecimal rateDivisor = BigDecimal.ONE.add(effectiveRate.divide(BigDecimal.valueOf(100)));

        BigDecimal mrpExclusive = line.mrpInclusive().divide(rateDivisor, 4, RoundingMode.HALF_UP);
        BigDecimal unitPriceExclusive = line.chargedUnitPriceInclusive().divide(rateDivisor, 4, RoundingMode.HALF_UP);

        BigDecimal taxableAmount = unitPriceExclusive.multiply(qty).setScale(2, RoundingMode.HALF_UP);

        // gstAmount is deliberately the REMAINDER (charged - taxable), not an
        // independently-rounded taxable*rate figure -- that guarantees
        // taxableAmount + gstAmount == the exact amount charged for this
        // line, every time, regardless of rounding direction. (The separate
        // CGST/SGST/IGST breakup used for the tax-summary section is
        // computed independently per rate group in buildInvoice -- any
        // paisa-level gap between the two is absorbed by the invoice's
        // Round Off line, same as a real GST invoice.)
        BigDecimal chargedForLine = line.chargedUnitPriceInclusive().multiply(qty).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gstAmount = chargedForLine.subtract(taxableAmount);

        BigDecimal discountPerUnit = mrpExclusive.subtract(unitPriceExclusive).max(BigDecimal.ZERO);
        BigDecimal discountAmount = discountPerUnit.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountPercent = mrpExclusive.signum() > 0
                ? discountPerUnit.divide(mrpExclusive, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return InvoiceItem.builder()
                .productName(line.productName())
                .variantInfo(line.variantInfo())
                .hsnCode(line.hsnCode())
                .mrpPerUnit(mrpExclusive.setScale(2, RoundingMode.HALF_UP))
                .quantity(line.quantity())
                .unit("Unit")
                .pricePerUnit(unitPriceExclusive.setScale(2, RoundingMode.HALF_UP))
                .discountAmount(discountAmount)
                .discountPercent(discountPercent)
                .gstRate(effectiveRate)
                .gstAmount(gstAmount)
                .taxableAmount(taxableAmount)
                .lineTotal(chargedForLine)
                .build();
    }

    private boolean isInterState(String customerState) {

        String sellerState = seller.getState() == null ? "" : seller.getState().trim();
        String custState = customerState == null ? "" : customerState.trim();

        if (custState.isEmpty()) {
            log.warn("Invoice generation: customer state is missing -- defaulting to inter-state (IGST) "
                    + "since intra-state cannot be confirmed. Seller state: '{}'.", sellerState);
            return true;
        }

        return !sellerState.equalsIgnoreCase(custState);
    }

    private Invoice buildInvoice(
            InvoiceOrderType orderType,
            String customerName, String customerEmail, String customerPhone,
            String addressLine1, String addressLine2, String city, String state, String postalCode, String country,
            List<LineInput> lines, boolean interState,
            String paymentMethod, String paymentStatus,
            BigDecimal shippingCharge) {

        Invoice invoice = Invoice.builder()
                .orderType(orderType)
                .invoiceDate(LocalDateTime.now())
                .sellerName(seller.getName())
                .sellerAddress(seller.getAddressLine())
                .sellerPhone(seller.getPhone())
                .sellerEmail(seller.getEmail())
                .sellerState(seller.getState())
                .sellerStateCode(seller.getStateCode())
                .sellerGstin(seller.getGstin())
                .sellerCin(seller.getCin())
                .customerName(customerName)
                .customerEmail(customerEmail)
                .customerPhone(customerPhone)
                .billingAddressLine1(addressLine1)
                .billingAddressLine2(addressLine2)
                .billingCity(city)
                .billingState(state)
                .billingPostalCode(postalCode)
                .billingCountry(country)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .build();

        BigDecimal taxableTotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal chargedTotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;

        for (LineInput line : lines) {

            InvoiceItem item = decomposeLine(line);
            item.setInvoice(invoice);
            invoice.getItems().add(item);

            taxableTotal = taxableTotal.add(item.getTaxableAmount());
            discountTotal = discountTotal.add(item.getDiscountAmount());
            chargedTotal = chargedTotal.add(item.getLineTotal());

            // CGST/SGST (or IGST) are computed PER ITEM, independently from
            // that item's own taxableAmount and rate -- never by splitting
            // a pre-rounded combined total -- so each figure is exactly
            // taxableAmount x rate(/2), rounded once. This is what a real
            // GST invoice shows and guarantees CGST always equals SGST.
            if (interState) {
                igstTotal = igstTotal.add(taxAt(item.getTaxableAmount(), item.getGstRate()));
            } else {
                BigDecimal half = taxAt(item.getTaxableAmount(), item.getGstRate().divide(BigDecimal.valueOf(2)));
                cgstTotal = cgstTotal.add(half);
                sgstTotal = sgstTotal.add(half);
            }
        }

        if (shippingCharge != null && shippingCharge.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setShippingCharge(shippingCharge);
            chargedTotal = chargedTotal.add(shippingCharge);

            // 18% GST included in shippingCharge:
            BigDecimal taxableShipping = shippingCharge.divide(BigDecimal.valueOf(1.18), 2, RoundingMode.HALF_UP);
            BigDecimal gstShipping = shippingCharge.subtract(taxableShipping);
            taxableTotal = taxableTotal.add(taxableShipping);

            if (interState) {
                igstTotal = igstTotal.add(gstShipping);
            } else {
                BigDecimal halfGst = gstShipping.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                cgstTotal = cgstTotal.add(halfGst);
                sgstTotal = sgstTotal.add(gstShipping.subtract(halfGst));
            }
        }

        invoice.setTaxableAmount(taxableTotal);
        invoice.setTotalDiscount(discountTotal);
        invoice.setCgstAmount(cgstTotal);
        invoice.setSgstAmount(sgstTotal);
        invoice.setIgstAmount(igstTotal);

        // Grand total is always exactly the sum of what was actually
        // charged per line (never independently recomputed), so it can
        // never drift from the real order total. Because CGST/SGST/IGST
        // above are each rounded independently per item/rate rather than
        // derived as a remainder, (taxable + CGST + SGST + IGST) can land
        // a paisa or two away from the grand total -- exactly like the
        // reference invoice's own "Round off" line -- so that gap is
        // captured explicitly here instead of silently adjusting a total.
        BigDecimal grandTotal = chargedTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxSummed = taxableTotal.add(cgstTotal).add(sgstTotal).add(igstTotal);
        BigDecimal roundOff = grandTotal.subtract(taxSummed);

        BigDecimal sanityBound = new BigDecimal("0.02").multiply(BigDecimal.valueOf(Math.max(lines.size(), 1)));
        if (roundOff.abs().compareTo(sanityBound) > 0) {
            log.warn("Invoice generation: unexpectedly large round-off {} for {} line(s) "
                    + "(taxable={}, cgst={}, sgst={}, igst={}, grandTotal={}) -- possible calculation bug.",
                    roundOff, lines.size(), taxableTotal, cgstTotal, sgstTotal, igstTotal, grandTotal);
        }

        invoice.setRoundOff(roundOff);
        invoice.setGrandTotal(grandTotal);
        invoice.setAmountInWords(NumberToWordsUtil.toIndianRupeeWords(grandTotal));

        return invoice;
    }

    /** taxableAmount x rate%, rounded once to 2dp -- the one place per-item tax figures are computed. */
    private BigDecimal taxAt(BigDecimal taxableAmount, BigDecimal ratePercent) {

        return taxableAmount.multiply(ratePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private Invoice persistWithInvoiceNumber(Invoice invoice) {

        invoice = invoiceRepository.save(invoice);

        invoice.setInvoiceNumber("INV-" + String.format("%6s", invoice.getId()).replace(' ', '0'));

        return invoiceRepository.save(invoice);
    }
}
