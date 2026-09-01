package com.stylenest.stylenest_backend.dto.admin;

import com.stylenest.stylenest_backend.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusUpdateRequest {

    @NotNull
    private OrderStatus orderStatus;

}