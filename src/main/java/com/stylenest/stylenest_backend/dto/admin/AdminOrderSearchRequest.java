package com.stylenest.stylenest_backend.dto.admin;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOrderSearchRequest {

    // Matches order ID, order number, registered customer name/email, or
    // guest email/name -- see OrderSpecification.search.
    private String keyword;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer sizePerPage = 20;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private String direction = "desc";
}
