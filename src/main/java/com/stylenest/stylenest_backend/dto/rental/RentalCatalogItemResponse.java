package com.stylenest.stylenest_backend.dto.rental;

import java.math.BigDecimal;
import java.util.List;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalCatalogItemResponse {

    private Long id;
    private String name;
    private String colour;
    private BigDecimal rentalPrice;
    private Integer displayOrder;
    private List<String> imageUrls;
}
