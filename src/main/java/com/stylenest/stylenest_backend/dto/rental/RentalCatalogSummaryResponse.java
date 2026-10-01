package com.stylenest.stylenest_backend.dto.rental;

import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.RentalCatalogStatus;

import lombok.*;

/** Lightweight row for the admin "Rental Catalogs" list -- no items payload. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalCatalogSummaryResponse {

    private Long id;
    private String name;
    private String shareToken;
    private RentalCatalogStatus status;
    private int itemCount;
    private LocalDateTime createdAt;
}
