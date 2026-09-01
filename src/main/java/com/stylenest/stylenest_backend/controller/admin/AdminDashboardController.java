package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.admin.DashboardResponse;
import com.stylenest.stylenest_backend.dto.admin.LatestOrderResponse;
import com.stylenest.stylenest_backend.dto.admin.LowStockProductResponse;
import com.stylenest.stylenest_backend.dto.admin.TopSellingProductResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.admin.AdminDashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {

        DashboardResponse response = dashboardService.getDashboard();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Dashboard fetched successfully",
                        response
                )
        );
    }
    
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<LowStockProductResponse>>> getLowStockProducts() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Low stock products fetched successfully",
                        dashboardService.getLowStockProducts()));
    }
    
    @GetMapping("/latest-orders")
    public ResponseEntity<ApiResponse<List<LatestOrderResponse>>> getLatestOrders() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Latest orders fetched successfully",
                        dashboardService.getLatestOrders()));
    }
    
    @GetMapping("/top-selling")
    public ResponseEntity<ApiResponse<List<TopSellingProductResponse>>> getTopSellingProducts() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Top selling products fetched successfully",
                        dashboardService.getTopSellingProducts()));
    }
}