package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.dto.order.GuestOrderRequest;
import com.stylenest.stylenest_backend.dto.order.OrderRequest;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.dto.order.OrderSummaryResponse;
import com.stylenest.stylenest_backend.entity.Order;
import com.stylenest.stylenest_backend.enums.PaymentMethod;

public interface OrderService {

    OrderResponse placeOrder(OrderRequest request);

    List<OrderSummaryResponse> getMyOrders();

    OrderResponse getOrderById(Long id);

    void cancelOrder(Long id);

    /** Ownership-checked the same way as getOrderById. */
    InvoiceResponse getInvoiceView(Long id);

    /** Ownership-checked the same way as getOrderById. */
    byte[] getInvoicePdf(Long id);

    // --- Internal, used only by PaymentServiceImpl (Razorpay integration) ---

    /**
     * Validates the current user's cart and reserves stock for an online
     * (non-COD) payment, reusing the exact same pricing/stock logic as
     * placeOrder -- but does NOT clear the cart (that only happens once the
     * payment is actually verified as successful). Re-calling this with an
     * unchanged cart while a payment is already in progress returns the
     * same order rather than creating a duplicate.
     */
    Order reserveOrderForOnlinePayment(PaymentMethod paymentMethod);

    void markOnlinePaymentPaid(Order order);

    void markOnlinePaymentFailed(Order order);

    // --- Guest checkout (used by GuestOrderServiceImpl / PaymentServiceImpl) ---

    OrderResponse placeGuestOrder(GuestOrderRequest request);

    Order reserveGuestOrderForOnlinePayment(GuestOrderRequest request);

}
