package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.bulk.BulkProductRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkProductResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.BulkProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Bulk product images are uploaded the same way retail product images are
 * -- POST /api/admin/images/upload (already generic, not tied to the retail
 * catalog) -- then the returned URL is passed as imageUrl in the request
 * body here. No separate bulk upload endpoint needed.
 */
@RestController
@RequestMapping("/api/admin/bulk/products")
@RequiredArgsConstructor
public class AdminBulkProductController {

    private final BulkProductService bulkProductService;

    @PostMapping
    public ResponseEntity<ApiResponse<BulkProductResponse>> createProduct(
            @Valid @RequestBody BulkProductRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bulk product created successfully", bulkProductService.createProduct(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BulkProductResponse>>> getAllProducts() {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk products fetched successfully", bulkProductService.getAllProducts()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BulkProductResponse>> getProductById(@PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk product fetched successfully", bulkProductService.getProductById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BulkProductResponse>> updateProduct(
            @PathVariable Long id, @Valid @RequestBody BulkProductRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk product updated successfully", bulkProductService.updateProduct(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {

        bulkProductService.deleteProduct(id);

        return ResponseEntity.ok(ApiResponse.success("Bulk product deleted successfully", null));
    }
}
