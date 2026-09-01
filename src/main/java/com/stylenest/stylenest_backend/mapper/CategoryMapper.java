package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.category.CategoryRequest;
import com.stylenest.stylenest_backend.dto.category.CategoryResponse;
import com.stylenest.stylenest_backend.entity.Category;

@Component
public class CategoryMapper {

    public Category toEntity(CategoryRequest request) {

        return Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .gender(request.getGender())
                .active(request.getActive())
                .build();
    }

    public CategoryResponse toResponse(Category category) {

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .gender(category.getGender())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public void updateEntity(Category category, CategoryRequest request) {

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setGender(request.getGender());
        category.setActive(request.getActive());

    }

}