package com.stylenest.stylenest_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.product.ProductVariantRequest;
import com.stylenest.stylenest_backend.dto.product.ProductVariantResponse;
import com.stylenest.stylenest_backend.dto.product.RenameColorGroupRequest;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.ProductVariantService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService variantService;

    @PostMapping("/products/{productId}/variants")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> createVariant(
            @PathVariable Long productId,
            @Valid @RequestBody ProductVariantRequest request) {

        ProductVariantResponse response = variantService.createVariant(productId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Variant created successfully", response));
    }

    @GetMapping("/products/{productId}/variants")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> getVariants(
            @PathVariable Long productId) {

        List<ProductVariantResponse> response = variantService.getVariantsByProduct(productId);

        return ResponseEntity.ok(
                ApiResponse.success("Variants fetched successfully", response));
    }

    @PutMapping("/variants/{variantId}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateVariant(
            @PathVariable Long variantId,
            @Valid @RequestBody ProductVariantRequest request) {

        ProductVariantResponse response = variantService.updateVariant(variantId, request);

        return ResponseEntity.ok(
                ApiResponse.success("Variant updated successfully", response));
    }

    // Color-group level edit: renames every existing size of one color to a new color in one
    // operation, instead of editing color per individual size variant.
    @PutMapping("/products/{productId}/colors/{color}/rename")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> renameColorGroup(
            @PathVariable Long productId,
            @PathVariable String color,
            @Valid @RequestBody RenameColorGroupRequest request) {

        List<ProductVariantResponse> response = variantService.renameColorGroup(productId, color, request);

        return ResponseEntity.ok(
                ApiResponse.success("Color updated successfully", response));
    }

    @DeleteMapping("/variants/{variantId}")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable Long variantId) {

        variantService.deleteVariant(variantId);

        return ResponseEntity.ok(
                ApiResponse.success("Variant deleted successfully", null));
    }
}