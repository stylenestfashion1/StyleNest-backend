package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.bulk.BulkCategoryRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkCategoryResponse;

public interface BulkCategoryService {

    BulkCategoryResponse createCategory(BulkCategoryRequest request);

    List<BulkCategoryResponse> getAllCategories();

    List<BulkCategoryResponse> getActiveCategories();

    BulkCategoryResponse getCategoryById(Long id);

    BulkCategoryResponse updateCategory(Long id, BulkCategoryRequest request);

    void deleteCategory(Long id);
}
