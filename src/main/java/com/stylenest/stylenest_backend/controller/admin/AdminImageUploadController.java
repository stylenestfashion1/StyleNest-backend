package com.stylenest.stylenest_backend.controller.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.upload.ImageUploadResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.ImageStorageService;

import lombok.RequiredArgsConstructor;

/**
 * Admin-only local file upload for product images -- an additional way to
 * get a usable imageUrl, alongside (not replacing) pasting an external
 * URL directly into POST /api/variants/{variantId}/images. This endpoint
 * only stores the file and returns its URL; attaching that URL to a
 * variant is still a separate call to the existing endpoint.
 *
 * No explicit SecurityConfig matcher is needed: everything under
 * /api/admin/** already requires ROLE_ADMIN via the existing catch-all
 * rule.
 */
@RestController
@RequestMapping("/api/admin/images")
@RequiredArgsConstructor
public class AdminImageUploadController {

    private final ImageStorageService imageStorageService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(
            @RequestParam("file") MultipartFile file) {

        ImageUploadResponse response = imageStorageService.store(file);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded successfully", response));
    }
}
