package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.admin.AdminOrderSearchRequest;
import com.stylenest.stylenest_backend.dto.admin.AdminOrderSummaryResponse;
import com.stylenest.stylenest_backend.dto.admin.OrderStatusUpdateRequest;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.dto.order.OrderResponse;
import com.stylenest.stylenest_backend.dto.order.OrderSummaryResponse;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentResponse;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentUpdateRequest;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.ShipmentService;
import com.stylenest.stylenest_backend.service.admin.AdminOrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final ShipmentService shipmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> getAllOrders() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Orders fetched successfully",
                        adminOrderService.getAllOrders()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<AdminOrderSummaryResponse>>> searchOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        AdminOrderSearchRequest request = AdminOrderSearchRequest.builder()
                .keyword(keyword)
                .page(page)
                .sizePerPage(size)
                .sortBy(sortBy)
                .direction(direction)
                .build();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Orders fetched successfully",
                        adminOrderService.searchOrders(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Order fetched successfully",
                        adminOrderService.getOrderById(id)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Order status updated successfully",
                        adminOrderService.updateOrderStatus(id, request)));
    }

    @PutMapping("/{id}/shipment")
    public ResponseEntity<ApiResponse<ShipmentResponse>> updateShipment(
            @PathVariable Long id,
            @Valid @RequestBody ShipmentUpdateRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Shipment updated successfully",
                        shipmentService.updateShipment(id, request)));
    }

    @GetMapping("/{id}/invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Invoice fetched successfully",
                        adminOrderService.getInvoiceView(id)));
    }

    @GetMapping("/{id}/invoice/pdf")
    public ResponseEntity<byte[]> getInvoicePdf(
            @PathVariable Long id) {

        byte[] pdf = adminOrderService.getInvoicePdf(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Invoice-" + id + ".pdf\"")
                .body(pdf);
    }
}
