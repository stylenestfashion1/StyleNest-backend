package com.stylenest.stylenest_backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.category.CategoryRequest;
import com.stylenest.stylenest_backend.dto.category.CategoryResponse;
import com.stylenest.stylenest_backend.dto.category.CategoryUpdateRequest;
import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.enums.Gender;
import com.stylenest.stylenest_backend.exception.CategoryAlreadyExistsException;
import com.stylenest.stylenest_backend.exception.CategoryHasProductsException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.CategoryMapper;
import com.stylenest.stylenest_backend.repository.CategoryRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.service.CategoryService;
import com.stylenest.stylenest_backend.util.SlugUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {

        if (categoryRepository.existsByNameAndGender(request.getName(), request.getGender())) {
            throw new CategoryAlreadyExistsException(
                    "Category '" + request.getName() + "' already exists for " + request.getGender() + ".");
        }

        Category category = categoryMapper.toEntity(request);

        category.setSlug(buildSlug(request.getName(), request.getGender()));

        category = categoryRepository.save(category);

        return categoryMapper.toResponse(category);
    }

    @Override
    public List<CategoryResponse> getAllCategories() {

        return categoryRepository.findAll()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    public List<CategoryResponse> getCategoriesByGender(Gender gender) {

        return categoryRepository.findByGender(gender)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    // Two categories can legitimately share a name across genders (e.g.
    // "Jeans" for MEN and "Jeans" for WOMEN), but the slug column is still
    // globally unique -- prefixing with gender keeps every slug distinct
    // without needing a second composite unique constraint.
    private String buildSlug(String name, Gender gender) {
        return gender.name().toLowerCase() + "-" + SlugUtil.generateSlug(name);
    }

    @Override
    public CategoryResponse getCategoryById(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category not found with id: " + id));

        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category not found with id: " + id));

        boolean nameOrGenderChanged = !category.getName().equalsIgnoreCase(request.getName())
                || category.getGender() != request.getGender();

        if (nameOrGenderChanged
                && categoryRepository.existsByNameAndGender(request.getName(), request.getGender())) {
            throw new CategoryAlreadyExistsException(
                    "Category '" + request.getName() + "' already exists for " + request.getGender() + ".");
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setGender(request.getGender());
        category.setActive(request.getActive());
        category.setSlug(buildSlug(request.getName(), request.getGender()));

        category = categoryRepository.save(category);

        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category not found with id: " + id));

        // CATEGORY DELETE must never imply PRODUCT DELETE. category_id is
        // NOT NULL on Product, so a product can't be silently orphaned
        // either -- the only safe option is to block deletion outright
        // while products are still assigned, and require the admin to
        // reassign/remove them first.
        if (productRepository.existsByCategory(category)) {
            throw new CategoryHasProductsException(
                    "Cannot delete category while products are assigned to it. "
                            + "Reassign or remove the products from this category first.");
        }

        categoryRepository.delete(category);
    }
}