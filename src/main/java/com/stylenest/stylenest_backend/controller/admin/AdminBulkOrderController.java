package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.bulk.BulkOrderResponse;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderStatusUpdateRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderSummaryResponse;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.BulkOrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/bulk/orders")
@RequiredArgsConstructor
public class AdminBulkOrderController {

    private final BulkOrderService bulkOrderService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BulkOrderSummaryResponse>>> getAllOrders() {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk orders fetched successfully", bulkOrderService.getAllOrders()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BulkOrderResponse>> getOrderById(@PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk order fetched successfully", bulkOrderService.getOrderById(id)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BulkOrderResponse>> updateOrderStatus(
            @PathVariable Long id, @Valid @RequestBody BulkOrderStatusUpdateRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk order status updated successfully",
                        bulkOrderService.updateOrderStatus(id, request)));
    }

    @GetMapping("/{id}/invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(@PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Invoice fetched successfully", bulkOrderService.getInvoiceView(id)));
    }

    @GetMapping("/{id}/invoice/pdf")
    public ResponseEntity<byte[]> getInvoicePdf(@PathVariable Long id) {

        byte[] pdf = bulkOrderService.getInvoicePdf(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Invoice-Bulk-" + id + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/{id}/invoice/resend-email")
    public ResponseEntity<ApiResponse<Void>> resendInvoiceEmail(@PathVariable Long id) {

        bulkOrderService.resendInvoiceEmail(id);

        return ResponseEntity.ok(ApiResponse.success("Invoice email resent", null));
    }
}
