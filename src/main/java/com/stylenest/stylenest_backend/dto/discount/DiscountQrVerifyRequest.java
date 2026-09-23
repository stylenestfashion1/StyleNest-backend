package com.stylenest.stylenest_backend.dto.discount;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountQrVerifyRequest {

    @NotBlank(message = "QR token is required")
    private String qrToken;
}
