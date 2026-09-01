package com.stylenest.stylenest_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.cart.AddToCartRequest;
import com.stylenest.stylenest_backend.dto.cart.CartResponse;
import com.stylenest.stylenest_backend.dto.cart.UpdateCartRequest;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.CartService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @Valid @RequestBody AddToCartRequest request) {

        CartResponse response = cartService.addToCart(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Item added to cart successfully",
                        response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Cart fetched successfully",
                        cartService.getCart()
                ));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateQuantity(
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Cart updated successfully",
                        cartService.updateQuantity(cartItemId, request)
                ));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @PathVariable Long cartItemId) {

        cartService.removeItem(cartItemId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Item removed successfully",
                        null
                ));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearCart() {

        cartService.clearCart();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Cart cleared successfully",
                        null
                ));
    }
}