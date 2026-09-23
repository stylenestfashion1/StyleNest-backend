package com.stylenest.stylenest_backend.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.cart.CartItemResponse;
import com.stylenest.stylenest_backend.dto.cart.CartResponse;
import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.CartItem;
import com.stylenest.stylenest_backend.entity.ProductImage;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.repository.ProductImageRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CartMapper {

    private final ProductImageRepository imageRepository;

    public CartItemResponse toCartItemResponse(CartItem cartItem) {

        ProductVariant variant = cartItem.getProductVariant();

        // Images belong to (product, color), shared across every size of
        // that color -- not owned by this specific size variant.
        List<ProductImage> images = imageRepository
                .findByProductIdAndColorOrderByDisplayOrderAsc(
                        variant.getProduct().getId(), variant.getColor());

        String imageUrl = images.isEmpty() ? null : images.get(0).getImageUrl();

        BigDecimal subTotal =
                cartItem.getPrice()
                        .multiply(
                                BigDecimal.valueOf(
                                        cartItem.getQuantity()));

        return CartItemResponse.builder()
                .cartItemId(cartItem.getId())
                .productId(
                        cartItem.getProductVariant()
                                .getProduct()
                                .getId())
                .productVariantId(
                        cartItem.getProductVariant()
                                .getId())
                .productName(
                        cartItem.getProductVariant()
                                .getProduct()
                                .getName())
                .color(
                        cartItem.getProductVariant()
                                .getColor())
                .size(
                        cartItem.getProductVariant()
                                .getSize()
                                .name())
                .imageUrl(imageUrl)
                .price(cartItem.getPrice())
                .quantity(cartItem.getQuantity())
                .subTotal(subTotal)
                .build();
    }

    public CartResponse toCartResponse(Cart cart) {

        List<CartItemResponse> items =
                cart.getItems()
                        .stream()
                        .map(this::toCartItemResponse)
                        .toList();

        BigDecimal totalPrice =
                items.stream()
                        .map(CartItemResponse::getSubTotal)
                        .reduce(BigDecimal.ZERO,
                                BigDecimal::add);

        int totalItems =
                items.stream()
                        .mapToInt(CartItemResponse::getQuantity)
                        .sum();

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(items)
                .totalPrice(totalPrice)
                .totalItems(totalItems)
                .build();
    }

}