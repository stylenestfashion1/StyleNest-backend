package com.stylenest.stylenest_backend.dto.rental;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalCatalogItemRequest {

    @NotBlank(message = "Lehenga name is required")
    private String name;

    @NotBlank(message = "Colour is required")
    private String colour;

    @NotNull(message = "Rental price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Rental price must be greater than 0")
    private BigDecimal rentalPrice;

    // Image URLs already uploaded via POST /api/admin/rental-catalogs/images/upload,
    // in the order they should be displayed. Optional -- an item can be
    // created first and have photos added on a later edit.
    private List<String> imageUrls;
}
