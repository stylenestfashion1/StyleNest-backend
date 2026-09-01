package com.stylenest.stylenest_backend.dto.wishlist;

import java.math.BigDecimal;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItemResponse {

    private Long wishlistItemId;

    private Long productId;

    /**
     * Null unless this item was added while viewing a specific variant.
     */
    private Long productVariantId;

    private String productName;

    private String slug;

    private String imageUrl;

    /**
     * Populated only when productVariantId is set.
     */
    private String color;

    /**
     * Populated only when productVariantId is set.
     */
    private String size;

    private BigDecimal price;

    private BigDecimal discountPrice;

}