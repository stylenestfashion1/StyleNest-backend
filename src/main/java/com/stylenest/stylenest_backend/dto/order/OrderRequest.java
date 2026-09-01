package com.stylenest.stylenest_backend.dto.order;

import com.stylenest.stylenest_backend.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    @NotNull
    private PaymentMethod paymentMethod;

}