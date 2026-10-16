package com.stylenest.stylenest_backend.service;

import java.util.Map;

import com.stylenest.stylenest_backend.dto.quickpay.QuickPaymentInitiateRequest;
import com.stylenest.stylenest_backend.dto.quickpay.QuickPaymentResponse;

public interface QuickPaymentService {

    QuickPaymentResponse initiateQuickPayment(QuickPaymentInitiateRequest request);

    String handleCallback(Map<String, String> formData);

    QuickPaymentResponse getPaymentStatus(String paymentNumber);
}
