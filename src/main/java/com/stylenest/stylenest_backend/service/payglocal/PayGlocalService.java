package com.stylenest.stylenest_backend.service.payglocal;

import java.util.Map;

import com.stylenest.stylenest_backend.dto.bulk.payment.PayGlocalInitiateResponse;

public interface PayGlocalService {

    PayGlocalInitiateResponse initiatePayment(String bulkOrderNumber);

    String handleCallback(Map<String, String> formData);

    void handleWebhook(String rawPayload, String jwsHeader);
}
