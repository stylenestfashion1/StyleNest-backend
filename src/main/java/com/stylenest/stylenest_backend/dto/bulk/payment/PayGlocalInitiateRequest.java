package com.stylenest.stylenest_backend.dto.bulk.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayGlocalInitiateRequest {

    @NotBlank(message = "Bulk order number is required")
    private String bulkOrderNumber;
}
