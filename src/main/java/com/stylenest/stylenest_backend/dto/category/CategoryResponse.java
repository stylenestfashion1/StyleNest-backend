package com.stylenest.stylenest_backend.dto.category;

import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.Gender;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private Long id;

    private String name;

    private String slug;

    private String description;

    private String imageUrl;

    private Gender gender;

    private Boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}