package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.wishlist.AddToWishlistRequest;
import com.stylenest.stylenest_backend.dto.wishlist.WishlistResponse;

public interface WishlistService {

    WishlistResponse addToWishlist(AddToWishlistRequest request);

    WishlistResponse getWishlist();

    void removeItem(Long wishlistItemId);

    void clearWishlist();

}