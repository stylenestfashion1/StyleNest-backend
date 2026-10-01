package com.stylenest.stylenest_backend.dto.rental;

import java.time.LocalDateTime;
import java.util.List;

import com.stylenest.stylenest_backend.enums.RentalCatalogStatus;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalCatalogResponse {

    private Long id;
    private String name;
    private String shareToken;
    private RentalCatalogStatus status;
    private List<RentalCatalogItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
