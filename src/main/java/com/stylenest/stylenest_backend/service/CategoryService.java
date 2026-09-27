package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.category.CategoryRequest;
import com.stylenest.stylenest_backend.dto.category.CategoryResponse;
import com.stylenest.stylenest_backend.dto.category.CategoryUpdateRequest;
import com.stylenest.stylenest_backend.enums.Gender;

public interface CategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    List<CategoryResponse> getAllCategories();

    List<CategoryResponse> getCategoriesByGender(Gender gender);

    CategoryResponse getCategoryById(Long id);

    // The public, canonical customer-facing lookup -- see
    // CategoryController GET /api/categories/slug/{slug}. The slug already
    // embeds the gender prefix (see CategoryServiceImpl.buildSlug), so the
    // caller passes the full stored value (e.g. "women-kurti"), not the
    // bare category name.
    CategoryResponse getCategoryBySlug(String slug);

    CategoryResponse updateCategory(Long id, CategoryUpdateRequest request);

    void deleteCategory(Long id);

}