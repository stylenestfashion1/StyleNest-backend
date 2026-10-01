package com.stylenest.stylenest_backend.dto.rental;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalCatalogRequest {

    @NotBlank(message = "Catalog name is required")
    private String name;
}
