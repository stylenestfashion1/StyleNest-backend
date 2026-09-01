package com.stylenest.stylenest_backend.dto.category;

import com.stylenest.stylenest_backend.enums.Gender;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryUpdateRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    private String description;

    private String imageUrl;

    @NotNull(message = "Gender is required")
    private Gender gender;

    private Boolean active;
}