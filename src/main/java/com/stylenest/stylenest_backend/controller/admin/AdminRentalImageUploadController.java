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
import com.stylenest.stylenest_backend.service.RentalImageStorageService;

import lombok.RequiredArgsConstructor;

/**
 * Kept as its own endpoint (not a reuse of POST /api/admin/images/upload)
 * specifically so rental photos land under uploads/rental/, never mixed
 * into uploads/products/ -- see RentalImageStorageService's javadoc.
 */
@RestController
@RequestMapping("/api/admin/rental-catalogs/images")
@RequiredArgsConstructor
public class AdminRentalImageUploadController {

    private final RentalImageStorageService rentalImageStorageService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(
            @RequestParam("file") MultipartFile file) {

        ImageUploadResponse response = rentalImageStorageService.store(file);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded successfully", response));
    }
}
