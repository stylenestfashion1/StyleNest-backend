package com.stylenest.stylenest_backend.dto.order;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestOrderTrackingRequest {

    @NotBlank(message = "Order ID is required")
    private String orderNumber;

    @NotBlank(message = "Phone number is required")
    private String phone;
}
