package com.stylenest.stylenest_backend.dto.payment;

import java.util.List;

import com.stylenest.stylenest_backend.dto.order.GuestOrderItemRequest;
import com.stylenest.stylenest_backend.dto.order.GuestShippingAddressRequest;
import com.stylenest.stylenest_backend.enums.Currency;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Guest analog of the (implicit, no-body) registered initiate call -- also
 * carries the not-yet-reserved order's contents, since a guest has no
 * server-side cart for OrderService to read from. No paymentMethod/upiVa/
 * bankCode fields: Razorpay Checkout itself is where the customer picks
 * card/UPI/netbanking/wallet, so this endpoint only ever means "start an
 * online payment."
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayGuestInitiateRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    private String guestEmail;

    @NotNull(message = "Shipping address is required")
    @Valid
    private GuestShippingAddressRequest shippingAddress;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<GuestOrderItemRequest> items;

    @NotNull(message = "Currency is required")
    private Currency currency;
}
