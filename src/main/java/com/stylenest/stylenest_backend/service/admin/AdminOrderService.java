package com.stylenest.stylenest_backend.service.admin;


import java.util.List;

import org.springframework.data.domain.Page;

import com.stylenest.stylenest_backend.dto.admin.AdminOrderSearchRequest;
import com.stylenest.stylenest_backend.dto.admin.AdminOrderSummaryResponse;
import com.stylenest.stylenest_backend.dto.admin.OrderStatusUpdateRequest;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.dto.order.OrderSummaryResponse;

public interface AdminOrderService {

    List<OrderSummaryResponse> getAllOrders();

    Page<AdminOrderSummaryResponse> searchOrders(AdminOrderSearchRequest request);

    OrderResponse getOrderById(Long id);

    OrderResponse updateOrderStatus(
            Long id,
            OrderStatusUpdateRequest request);

    InvoiceResponse getInvoiceView(Long id);

    byte[] getInvoicePdf(Long id);

    /** Re-sends the already-generated invoice PDF to the order's customer/guest email. */
    void resendInvoiceEmail(Long id);

}
