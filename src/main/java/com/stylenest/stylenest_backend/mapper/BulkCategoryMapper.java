package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.bulk.BulkCategoryRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkCategoryResponse;
import com.stylenest.stylenest_backend.entity.BulkCategory;
import com.stylenest.stylenest_backend.enums.Gender;

@Component
public class BulkCategoryMapper {

    public BulkCategory toEntity(BulkCategoryRequest request) {

        return BulkCategory.builder()
                .name(request.getName())
                .gender(request.getGender() != null ? request.getGender() : Gender.WOMEN)
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .active(request.getActive())
                .build();
    }

    public BulkCategoryResponse toResponse(BulkCategory category) {

        return BulkCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .gender(category.getGender())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public void updateEntity(BulkCategory category, BulkCategoryRequest request) {

        category.setName(request.getName());
        if (request.getGender() != null) {
            category.setGender(request.getGender());
        }
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setActive(request.getActive());
    }
}
