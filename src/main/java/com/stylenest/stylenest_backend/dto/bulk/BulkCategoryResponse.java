package com.stylenest.stylenest_backend.dto.bulk;

import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.Gender;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCategoryResponse {

    private Long id;
    private String name;
    private Gender gender;
    private String slug;
    private String description;
    private String imageUrl;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
