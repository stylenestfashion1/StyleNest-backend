package com.stylenest.stylenest_backend.dto.wishlist;

import java.util.List;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistResponse {

    private Long wishlistId;

    private List<WishlistItemResponse> items;

    private Integer totalItems;

}