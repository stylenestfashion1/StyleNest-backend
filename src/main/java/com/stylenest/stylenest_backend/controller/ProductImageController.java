package com.stylenest.stylenest_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.product.ProductImageRequest;
import com.stylenest.stylenest_backend.dto.product.ProductImageResponse;
import com.stylenest.stylenest_backend.dto.product.ReorderImagesRequest;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.ProductImageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService imageService;

    @PostMapping("/products/{productId}/colors/{color}/images")
    public ResponseEntity<ApiResponse<ProductImageResponse>> addImage(
            @PathVariable Long productId,
            @PathVariable String color,
            @Valid @RequestBody ProductImageRequest request) {

        ProductImageResponse response = imageService.addImage(productId, color, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image added successfully", response));
    }

    @GetMapping("/products/{productId}/colors/{color}/images")
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> getImages(
            @PathVariable Long productId,
            @PathVariable String color) {

        List<ProductImageResponse> response = imageService.getImagesByProductAndColor(productId, color);

        return ResponseEntity.ok(
                ApiResponse.success("Images fetched successfully", response));
    }

    @PutMapping("/products/{productId}/colors/{color}/images/reorder")
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> reorderImages(
            @PathVariable Long productId,
            @PathVariable String color,
            @Valid @RequestBody ReorderImagesRequest request) {

        List<ProductImageResponse> response =
                imageService.reorderImages(productId, color, request.getOrderedImageIds());

        return ResponseEntity.ok(
                ApiResponse.success("Images reordered successfully", response));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable Long imageId) {

        imageService.deleteImage(imageId);

        return ResponseEntity.ok(
                ApiResponse.success("Image deleted successfully", null));
    }
}
