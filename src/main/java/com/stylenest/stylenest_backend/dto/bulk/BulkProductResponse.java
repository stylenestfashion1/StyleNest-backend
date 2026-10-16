package com.stylenest.stylenest_backend.dto.bulk;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.Gender;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkProductResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    @Builder.Default
    private java.util.List<String> imageUrls = new java.util.ArrayList<>();
    private BigDecimal price;
    private Integer minOrderQuantity;
    private Integer availableStock;
    private String hsnCode;
    private Long categoryId;
    private String categoryName;
    private Gender categoryGender;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
