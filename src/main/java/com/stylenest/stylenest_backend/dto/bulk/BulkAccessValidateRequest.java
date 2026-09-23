package com.stylenest.stylenest_backend.dto.bulk;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAccessValidateRequest {

    @NotBlank(message = "Access code is required")
    private String accessToken;
}
