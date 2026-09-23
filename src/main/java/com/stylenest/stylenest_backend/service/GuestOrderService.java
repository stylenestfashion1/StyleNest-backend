package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.order.GuestOrderTrackingRequest;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;

public interface GuestOrderService {

    OrderResponse placeOrder(GuestOrderRequest request);

    /** Requires the order's orderNumber AND the phone it was placed with to match. */
    OrderResponse trackOrder(GuestOrderTrackingRequest request);

    /** Same phone-verification gate as trackOrder. */
    InvoiceResponse getInvoiceView(String orderNumber, String phone);

    /** Same phone-verification gate as trackOrder. */
    byte[] getInvoicePdf(String orderNumber, String phone);
}
