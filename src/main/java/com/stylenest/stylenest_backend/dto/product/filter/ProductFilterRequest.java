package com.stylenest.stylenest_backend.dto.product.filter;

import java.math.BigDecimal;

import com.stylenest.stylenest_backend.enums.Gender;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFilterRequest {

    private String keyword;

    private Long categoryId;

    private Gender gender;

    private String color;

    private String size;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private Boolean featured;

    private Boolean trending;

    private Boolean active;

    
    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer sizePerPage = 12;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private String direction = "desc";
}