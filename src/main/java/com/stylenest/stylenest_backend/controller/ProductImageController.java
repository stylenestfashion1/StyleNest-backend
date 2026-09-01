package com.stylenest.stylenest_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.product.ProductImageRequest;
import com.stylenest.stylenest_backend.dto.product.ProductImageResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.ProductImageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService imageService;

    @PostMapping("/variants/{variantId}/images")
    public ResponseEntity<ApiResponse<ProductImageResponse>> addImage(
            @PathVariable Long variantId,
            @Valid @RequestBody ProductImageRequest request) {

        ProductImageResponse response = imageService.addImage(variantId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image added successfully", response));
    }

    @GetMapping("/variants/{variantId}/images")
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> getImages(
            @PathVariable Long variantId) {

        List<ProductImageResponse> response = imageService.getImagesByVariant(variantId);

        return ResponseEntity.ok(
                ApiResponse.success("Images fetched successfully", response));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable Long imageId) {

        imageService.deleteImage(imageId);

        return ResponseEntity.ok(
                ApiResponse.success("Image deleted successfully", null));
    }
}