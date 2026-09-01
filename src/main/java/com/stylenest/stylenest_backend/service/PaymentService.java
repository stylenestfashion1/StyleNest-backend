package com.stylenest.stylenest_backend.service;

import java.util.Map;

import com.stylenest.stylenest_backend.dto.payment.EasebuzzInitiateRequest;
import com.stylenest.stylenest_backend.dto.payment.EasebuzzInitiateResponse;
import com.stylenest.stylenest_backend.dto.payment.GuestPaymentInitiateRequest;

public interface PaymentService {

    EasebuzzInitiateResponse initiate(EasebuzzInitiateRequest request);

    EasebuzzInitiateResponse initiateForGuest(GuestPaymentInitiateRequest request);

    /**
     * Handles the Easebuzz SURL/FURL POST. Verifies the response hash,
     * updates the matching order's payment status, and returns the
     * frontend URL the customer's browser should be redirected to.
     */
    String handleCallback(Map<String, String> responseFields);
}
