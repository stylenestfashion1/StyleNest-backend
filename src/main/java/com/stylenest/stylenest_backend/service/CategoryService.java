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

    CategoryResponse updateCategory(Long id, CategoryUpdateRequest request);

    void deleteCategory(Long id);

}