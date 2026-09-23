package com.stylenest.stylenest_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.bulk.BulkCategoryResponse;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkOrderResponse;
import com.stylenest.stylenest_backend.dto.bulk.BulkProductResponse;
import com.stylenest.stylenest_backend.entity.BulkAccessToken;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.security.BulkAccessInterceptor;
import com.stylenest.stylenest_backend.service.BulkCategoryService;
import com.stylenest.stylenest_backend.service.BulkOrderService;
import com.stylenest.stylenest_backend.service.BulkProductService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Every route here requires a valid X-Bulk-Token header -- enforced by
 * BulkAccessInterceptor before any of these methods run (see WebMvcConfig).
 * The resolved, already-validated token is read back from the request
 * attribute the interceptor sets, never re-looked-up here.
 */
@RestController
@RequestMapping("/api/bulk/customer")
@RequiredArgsConstructor
public class BulkCustomerController {

    private final BulkCategoryService bulkCategoryService;
    private final BulkProductService bulkProductService;
    private final BulkOrderService bulkOrderService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<BulkCategoryResponse>>> getCategories() {

        return ResponseEntity.ok(
                ApiResponse.success("Categories fetched successfully", bulkCategoryService.getActiveCategories()));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<BulkProductResponse>>> getProducts(
            @RequestParam(required = false) Long categoryId) {

        return ResponseEntity.ok(
                ApiResponse.success("Products fetched successfully", bulkProductService.getActiveProducts(categoryId)));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<BulkProductResponse>> getProductById(@PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Product fetched successfully", bulkProductService.getActiveProductById(id)));
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<BulkOrderResponse>> placeOrder(
            @Valid @RequestBody BulkOrderRequest request,
            HttpServletRequest httpRequest) {

        BulkAccessToken token = (BulkAccessToken)
                httpRequest.getAttribute(BulkAccessInterceptor.BULK_TOKEN_REQUEST_ATTRIBUTE);

        BulkOrderResponse response = bulkOrderService.placeOrder(token, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bulk order placed successfully", response));
    }
}
