package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.product.ProductJeansCodeResponse;
import com.stylenest.stylenest_backend.dto.product.ProductRequest;
import com.stylenest.stylenest_backend.dto.product.ProductResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request) {

        ProductResponse response = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Product created successfully",
                        response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {

        List<ProductResponse> response =
                productService.getAllProducts();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Products fetched successfully",
                        response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable Long id) {

        ProductResponse response =
                productService.getProductById(id);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Product fetched successfully",
                        response));
    }

    // Admin-only internal identification code -- deliberately served from
    // its own tiny endpoint/DTO rather than folded into ProductResponse,
    // since that DTO is also returned by the public, unauthenticated
    // GET /api/products/** (see ProductController). This whole controller
    // is already ROLE_ADMIN-gated (SecurityConfig: /api/admin/** ->
    // hasRole("ADMIN")), so nothing further is needed to keep this private.
    @GetMapping("/jeans-codes")
    public ResponseEntity<ApiResponse<List<ProductJeansCodeResponse>>> getAllJeansCodes() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Jeans codes fetched successfully",
                        productService.getAllJeansCodes()));
    }

    @GetMapping("/{id}/jeans-code")
    public ResponseEntity<ApiResponse<ProductJeansCodeResponse>> getJeansCode(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Jeans code fetched successfully",
                        productService.getJeansCode(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        ProductResponse response =
                productService.updateProduct(id, request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Product updated successfully",
                        response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id) {

        productService.deleteProduct(id);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Product deleted successfully",
                        null));
    }
}