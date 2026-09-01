package com.stylenest.stylenest_backend.dto.order;

import java.util.List;

import com.stylenest.stylenest_backend.enums.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestOrderRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    private String guestEmail;

    @NotNull(message = "Shipping address is required")
    @Valid
    private GuestShippingAddressRequest shippingAddress;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<GuestOrderItemRequest> items;
}
