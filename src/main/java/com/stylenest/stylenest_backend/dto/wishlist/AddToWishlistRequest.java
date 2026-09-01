package com.stylenest.stylenest_backend.dto.wishlist;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToWishlistRequest {

    @NotNull(message = "Product Id is required")
    private Long productId;

    /**
     * Optional: the specific variant the customer was viewing when they
     * clicked "add to wishlist". Must belong to productId when provided.
     */
    private Long productVariantId;

}