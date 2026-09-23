package com.stylenest.stylenest_backend.dto.bulk;

import com.stylenest.stylenest_backend.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkOrderStatusUpdateRequest {

    @NotNull(message = "Order status is required")
    private OrderStatus orderStatus;
}
