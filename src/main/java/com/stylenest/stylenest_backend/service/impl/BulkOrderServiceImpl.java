package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.bulk.BulkOrderItemRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderResponse;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderStatusUpdateRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderSummaryResponse;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.entity.BulkAccessToken;
import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.entity.BulkOrderItem;
import com.stylenest.stylenest_backend.entity.BulkProduct;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.BulkTokenAlreadyAssignedException;
import com.stylenest.stylenest_backend.exception.InsufficientStockException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.BulkOrderMapper;
import com.stylenest.stylenest_backend.repository.BulkAccessTokenRepository;
import com.stylenest.stylenest_backend.repository.BulkOrderRepository;
import com.stylenest.stylenest_backend.repository.BulkProductRepository;
import com.stylenest.stylenest_backend.service.BulkOrderService;
import com.stylenest.stylenest_backend.service.EmailService;
import com.stylenest.stylenest_backend.service.InvoiceGenerationService;
import com.stylenest.stylenest_backend.service.InvoiceService;

import lombok.RequiredArgsConstructor;

/**
 * Deliberately COD-only for now (see PaymentMethod check in placeOrder) --
 * these are phone-negotiated wholesale deals settled directly with the shop,
 * not run through the Cashfree online-payment flow the retail store uses.
 * Nothing here prevents adding online payment later; it just isn't wired up
 * yet, and rejecting anything but COD with a clear message is safer than
 * silently accepting a payment method with no real gateway behind it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BulkOrderServiceImpl implements BulkOrderService {

    private final BulkOrderRepository bulkOrderRepository;
    private final BulkProductRepository bulkProductRepository;
    private final BulkAccessTokenRepository bulkAccessTokenRepository;
    private final BulkOrderMapper bulkOrderMapper;
    private final InvoiceGenerationService invoiceGenerationService;
    private final InvoiceService invoiceService;
    private final EmailService emailService;

    @Override
    public BulkOrderResponse placeOrder(BulkAccessToken token, BulkOrderRequest request) {

        if (request.getPaymentMethod() != PaymentMethod.COD) {
            throw new BadRequestException(
                    "Only Cash on Delivery is currently supported for bulk orders. "
                            + "Please contact the shop directly for other payment arrangements.");
        }

        enforceOneCustomerPerToken(token, request);

        BulkOrder order = BulkOrder.builder()
                .bulkOrderNumber(generateBulkOrderNumber())
                .accessToken(token)
                .customerName(request.getCustomerName().trim())
                .customerEmail(request.getCustomerEmail().trim())
                .customerPhone(request.getCustomerPhone().trim())
                .shippingAddressLine1(request.getAddressLine1().trim())
                .shippingAddressLine2(request.getAddressLine2())
                .shippingCity(request.getCity().trim())
                .shippingState(request.getState().trim())
                .shippingPostalCode(request.getPostalCode().trim())
                .shippingCountry(request.getCountry().trim())
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .orderStatus(OrderStatus.PENDING)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (BulkOrderItemRequest itemRequest : request.getItems()) {

            BulkProduct product = bulkProductRepository.findById(itemRequest.getBulkProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bulk product not found."));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new BadRequestException(
                        product.getName() + " is no longer available for bulk order.");
            }

            if (itemRequest.getQuantity() < product.getMinOrderQuantity()) {
                throw new BadRequestException(
                        product.getName() + " requires a minimum order quantity of "
                                + product.getMinOrderQuantity() + ".");
            }

            if (product.getAvailableStock() != null && itemRequest.getQuantity() > product.getAvailableStock()) {
                throw new InsufficientStockException(
                        product.getName() + " has only " + product.getAvailableStock()
                                + " piece(s) available for bulk order.");
            }

            if (product.getAvailableStock() != null) {
                product.setAvailableStock(product.getAvailableStock() - itemRequest.getQuantity());
                bulkProductRepository.save(product);
            }

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            BulkOrderItem item = BulkOrderItem.builder()
                    .bulkOrder(order)
                    .bulkProduct(product)
                    .productNameSnapshot(product.getName())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .build();

            order.getItems().add(item);

            totalAmount = totalAmount.add(subtotal);
        }

        order.setTotalAmount(totalAmount);

        order = bulkOrderRepository.save(order);

        sendInvoiceEmailIfPossible(order);

        return bulkOrderMapper.toResponse(order);
    }

    /**
     * Bulk orders are always finalized immediately at placement (COD-only,
     * no separate payment-confirmation step), so invoice generation and the
     * invoice email happen right here -- mirroring where retail does it for
     * COD orders. A failure here must never fail the order itself; see the
     * same rationale on OrderServiceImpl.sendConfirmationEmailIfNeeded.
     */
    private void sendInvoiceEmailIfPossible(BulkOrder order) {

        try {

            var invoice = invoiceGenerationService.generateForBulkOrder(order);
            byte[] pdf = invoiceService.generatePdf(invoice);

            emailService.sendInvoiceEmail(
                    order.getCustomerEmail(), order.getCustomerName(), invoice.getInvoiceNumber(),
                    order.getBulkOrderNumber(), order.getTotalAmount(), pdf);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Enforces "one customer per token": the first order placed with a
     * token binds it to that customer's normalized email+phone; every
     * later order attempt with the same token must match both, or it's
     * rejected with the same generic message a completely invalid token
     * would get (never reveals *why* it failed).
     */
    private void enforceOneCustomerPerToken(BulkAccessToken token, BulkOrderRequest request) {

        String normalizedEmail = normalizeEmail(request.getCustomerEmail());
        String normalizedPhone = normalizePhone(request.getCustomerPhone());

        if (token.getAssignedCustomerEmail() == null) {

            token.setAssignedCustomerName(request.getCustomerName().trim());
            token.setAssignedCustomerEmail(normalizedEmail);
            token.setAssignedCustomerPhone(normalizedPhone);
            token.setFirstUsedAt(LocalDateTime.now());

        } else if (!token.getAssignedCustomerEmail().equals(normalizedEmail)
                || !token.getAssignedCustomerPhone().equals(normalizedPhone)) {

            throw new BulkTokenAlreadyAssignedException(
                    "This access code is already assigned. Please contact the shop.");
        }

        token.setLastUsedAt(LocalDateTime.now());

        bulkAccessTokenRepository.save(token);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[\\s\\-()]", "");
    }

    private String generateBulkOrderNumber() {

        return "BLK-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BulkOrderSummaryResponse> getAllOrders() {

        return bulkOrderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(bulkOrderMapper::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BulkOrderResponse getOrderById(Long id) {

        return bulkOrderMapper.toResponse(findById(id));
    }

    @Override
    public BulkOrderResponse updateOrderStatus(Long id, BulkOrderStatusUpdateRequest request) {

        BulkOrder order = findById(id);

        order.setOrderStatus(request.getOrderStatus());

        order = bulkOrderRepository.save(order);

        return bulkOrderMapper.toResponse(order);
    }

    @Override
    public InvoiceResponse getInvoiceView(Long id) {

        return invoiceService.buildView(invoiceGenerationService.generateForBulkOrder(findById(id)));
    }

    @Override
    public byte[] getInvoicePdf(Long id) {

        return invoiceService.generatePdf(invoiceGenerationService.generateForBulkOrder(findById(id)));
    }

    @Override
    public void resendInvoiceEmail(Long id) {

        BulkOrder order = findById(id);
        var invoice = invoiceGenerationService.generateForBulkOrder(order);
        byte[] pdf = invoiceService.generatePdf(invoice);

        emailService.sendInvoiceEmail(
                order.getCustomerEmail(), order.getCustomerName(), invoice.getInvoiceNumber(),
                order.getBulkOrderNumber(), order.getTotalAmount(), pdf);
    }

    private BulkOrder findById(Long id) {

        return bulkOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulk order not found with id: " + id));
    }
}
