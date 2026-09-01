package com.stylenest.stylenest_backend.dto.payment;

import com.stylenest.stylenest_backend.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EasebuzzInitiateRequest {

    @NotNull
    private PaymentMethod paymentMethod;

    // Required only when paymentMethod = UPI (customer's own UPI ID, e.g. "name@okhdfcbank")
    private String upiVa;

    // Required only when paymentMethod = NETBANKING (Easebuzz bank code, e.g. "HDFCB")
    private String bankCode;
}
