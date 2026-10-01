package com.stylenest.stylenest_backend.dto.rental;

import java.util.List;

import lombok.*;

/**
 * What a customer's browser actually receives at GET /api/rental-catalogs/{shareToken}.
 * Deliberately excludes the catalog's internal numeric id and status enum --
 * the share token already gates access, and "ACTIVE" is the only status
 * that ever reaches this DTO at all (see RentalCatalogController).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalCatalogPublicResponse {

    private String name;
    private List<RentalCatalogItemPublicResponse> items;
}
