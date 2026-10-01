package com.stylenest.stylenest_backend.dto.rental;

import java.math.BigDecimal;
import java.util.List;

import lombok.*;

/**
 * The item's own id IS exposed here (unlike the catalog's internal id) --
 * the customer's browser needs a stable identifier to key its local
 * favourites against, and an item id carries no information an attacker
 * could use against anything else (no auth, no ordering, no payment is
 * ever driven by it).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalCatalogItemPublicResponse {

    private Long id;
    private String name;
    private String colour;
    private BigDecimal rentalPrice;
    private List<String> imageUrls;
}
