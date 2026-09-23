package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.bulk.BulkOrderRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderResponse;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderStatusUpdateRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderSummaryResponse;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.entity.BulkAccessToken;

public interface BulkOrderService {

    BulkOrderResponse placeOrder(BulkAccessToken token, BulkOrderRequest request);

    List<BulkOrderSummaryResponse> getAllOrders();

    BulkOrderResponse getOrderById(Long id);

    BulkOrderResponse updateOrderStatus(Long id, BulkOrderStatusUpdateRequest request);

    InvoiceResponse getInvoiceView(Long id);

    byte[] getInvoicePdf(Long id);

    /** Re-sends the already-generated invoice PDF to the order's customer email. */
    void resendInvoiceEmail(Long id);
}
