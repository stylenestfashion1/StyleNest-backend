package com.stylenest.stylenest_backend.service;

import java.math.BigDecimal;

import com.stylenest.stylenest_backend.dto.email.OrderConfirmationEmailData;
import com.stylenest.stylenest_backend.enums.OrderStatus;

public interface EmailService {

    void sendOtpEmail(String toEmail, String otp);

    void sendOrderConfirmationEmail(String toEmail, OrderConfirmationEmailData data, byte[] invoicePdfBytes);

    void sendWelcomeEmail(String toEmail, String customerName);

    void sendOrderStatusUpdateEmail(String toEmail, String customerName, String orderNumber, OrderStatus status);

    /**
     * A generic "here's your invoice" email -- used for bulk orders (which
     * have no richer order-confirmation template) and as the target of the
     * admin "Resend Invoice Email" action for either order type.
     */
    void sendInvoiceEmail(
            String toEmail, String customerName, String invoiceNumber,
            String orderReference, BigDecimal totalAmount, byte[] invoicePdfBytes);

    void sendShipmentUpdateEmail(
            String toEmail, String customerName, String orderNumber,
            String courierName, String trackingNumber, com.stylenest.stylenest_backend.enums.ShipmentStatus shipmentStatus);
}
