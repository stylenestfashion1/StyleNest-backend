package com.stylenest.stylenest_backend.dto.bulk;

import com.stylenest.stylenest_backend.enums.Gender;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCategoryRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    @Builder.Default
    private Gender gender = Gender.WOMEN;

    private String description;

    private String imageUrl;

    @Builder.Default
    private Boolean active = true;
}
