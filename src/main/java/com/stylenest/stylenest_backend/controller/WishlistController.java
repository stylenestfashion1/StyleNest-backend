package com.stylenest.stylenest_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.wishlist.AddToWishlistRequest;
import com.stylenest.stylenest_backend.dto.wishlist.WishlistResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.WishlistService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<WishlistResponse>> addToWishlist(
            @Valid @RequestBody AddToWishlistRequest request) {

        WishlistResponse response = wishlistService.addToWishlist(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Product added to wishlist successfully",
                        response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<WishlistResponse>> getWishlist() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Wishlist fetched successfully",
                        wishlistService.getWishlist()
                ));
    }

    @DeleteMapping("/items/{wishlistItemId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @PathVariable Long wishlistItemId) {

        wishlistService.removeItem(wishlistItemId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Item removed successfully",
                        null
                ));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearWishlist() {

        wishlistService.clearWishlist();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Wishlist cleared successfully",
                        null
                ));
    }
}