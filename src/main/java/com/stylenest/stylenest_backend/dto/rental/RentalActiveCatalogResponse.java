package com.stylenest.stylenest_backend.dto.rental;

import lombok.*;

/**
 * Backs the main-nav "Rentals" entry point -- the frontend resolves this
 * once, then redirects into the existing /rental/{shareToken} route. No
 * items/pricing payload here; that's still exclusively
 * RentalCatalogPublicResponse's job once the real token is known.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalActiveCatalogResponse {

    private boolean available;
    private String shareToken;
}