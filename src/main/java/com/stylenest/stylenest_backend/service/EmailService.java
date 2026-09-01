package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.email.OrderConfirmationEmailData;
import com.stylenest.stylenest_backend.enums.OrderStatus;

public interface EmailService {

    void sendOtpEmail(String toEmail, String otp);

    void sendOrderConfirmationEmail(String toEmail, OrderConfirmationEmailData data, byte[] invoicePdfBytes);

    void sendWelcomeEmail(String toEmail, String customerName);

    void sendOrderStatusUpdateEmail(String toEmail, String customerName, String orderNumber, OrderStatus status);

}
