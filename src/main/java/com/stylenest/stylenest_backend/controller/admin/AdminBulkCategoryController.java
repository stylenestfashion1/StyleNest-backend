package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.bulk.BulkCategoryRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkCategoryResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.BulkCategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/bulk/categories")
@RequiredArgsConstructor
public class AdminBulkCategoryController {

    private final BulkCategoryService bulkCategoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<BulkCategoryResponse>> createCategory(
            @Valid @RequestBody BulkCategoryRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bulk category created successfully", bulkCategoryService.createCategory(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BulkCategoryResponse>>> getAllCategories() {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk categories fetched successfully", bulkCategoryService.getAllCategories()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BulkCategoryResponse>> getCategoryById(@PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk category fetched successfully", bulkCategoryService.getCategoryById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BulkCategoryResponse>> updateCategory(
            @PathVariable Long id, @Valid @RequestBody BulkCategoryRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk category updated successfully", bulkCategoryService.updateCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {

        bulkCategoryService.deleteCategory(id);

        return ResponseEntity.ok(ApiResponse.success("Bulk category deleted successfully", null));
    }
}
