package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stylenest.stylenest_backend.dto.quickpay.QuickPaymentResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.QuickPaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping({"/api/admin/bulk/quick-payments", "/api/admin/quick-payments"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuickPaymentController {

    private final QuickPaymentService quickPaymentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuickPaymentResponse>>> getAllQuickPayments() {
        log.info("Admin fetching all quick payments");
        List<QuickPaymentResponse> list = quickPaymentService.getAllQuickPayments();
        return ResponseEntity.ok(ApiResponse.success("Quick payments retrieved successfully", list));
    }
}
