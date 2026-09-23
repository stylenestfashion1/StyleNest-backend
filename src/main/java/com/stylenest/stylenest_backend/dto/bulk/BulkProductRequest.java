package com.stylenest.stylenest_backend.dto.bulk;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    private String imageUrl;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    @NotNull(message = "Minimum order quantity is required")
    @Min(value = 1, message = "Minimum order quantity must be at least 1")
    private Integer minOrderQuantity;

    // Optional: leave null for "not tracked here".
    private Integer availableStock;

    // Optional -- shown on invoices but not required to create a bulk
    // product; see InvoiceGenerationServiceImpl.
    private String hsnCode;

    // Deliberately NO gstRate field here -- GST is never manually entered.
    // See InvoiceGenerationServiceImpl.resolveApparelGstRate.

    @NotNull(message = "Category is required")
    private Long categoryId;

    @Builder.Default
    private Boolean active = true;
}
