package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.cart.AddToCartRequest;
import com.stylenest.stylenest_backend.dto.cart.CartResponse;
import com.stylenest.stylenest_backend.dto.cart.UpdateCartRequest;

public interface CartService {

    CartResponse addToCart(AddToCartRequest request);

    CartResponse getCart();

    CartResponse updateQuantity(Long cartItemId,
                                UpdateCartRequest request);

    void removeItem(Long cartItemId);

    void clearCart();

}