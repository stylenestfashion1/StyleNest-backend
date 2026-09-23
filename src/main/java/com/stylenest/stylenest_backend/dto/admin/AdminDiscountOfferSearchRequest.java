package com.stylenest.stylenest_backend.dto.admin;

import java.time.LocalDate;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDiscountOfferSearchRequest {

    // Matches customer name or mobile number -- see DiscountOfferSpecification.search.
    private String keyword;

    private Integer discountPercentage;

    private LocalDate fromDate;

    private LocalDate toDate;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer sizePerPage = 20;

    @Builder.Default
    private String sortBy = "generatedAt";

    @Builder.Default
    private String direction = "desc";
}
