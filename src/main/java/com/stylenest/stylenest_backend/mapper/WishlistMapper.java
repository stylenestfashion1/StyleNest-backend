package com.stylenest.stylenest_backend.mapper;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.wishlist.WishlistItemResponse;
import com.stylenest.stylenest_backend.dto.wishlist.WishlistResponse;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.Wishlist;
import com.stylenest.stylenest_backend.entity.WishlistItem;

@Component
public class WishlistMapper {

    /**
     * @param productThumbnails productId -> thumbnail URL for items with no
     *                          stored variant, resolved by
     *                          ProductThumbnailResolver using the same
     *                          logic as ProductResponse.thumbnailUrl.
     * @param variantImages     variantId -> image URL for items with a
     *                          stored variant, resolved by
     *                          VariantImageResolver from that variant's
     *                          own images.
     */
    public WishlistItemResponse toWishlistItemResponse(
            WishlistItem item,
            Map<Long, String> productThumbnails,
            Map<Long, String> variantImages) {

        ProductVariant variant = item.getProductVariant();

        String imageUrl;
        Long variantId = null;
        String color = null;
        String size = null;

        if (variant != null) {
            variantId = variant.getId();
            imageUrl = variantImages.get(variantId);
            color = variant.getColor();
            size = variant.getSize().name();
        } else {
            imageUrl = productThumbnails.get(item.getProduct().getId());
        }

        return WishlistItemResponse.builder()
                .wishlistItemId(item.getId())
                .productId(item.getProduct().getId())
                .productVariantId(variantId)
                .productName(item.getProduct().getName())
                .slug(item.getProduct().getSlug())
                .imageUrl(imageUrl)
                .color(color)
                .size(size)
                .price(item.getProduct().getPrice())
                .discountPrice(item.getProduct().getDiscountPrice())
                .build();
    }

    public WishlistResponse toWishlistResponse(
            Wishlist wishlist,
            Map<Long, String> productThumbnails,
            Map<Long, String> variantImages) {

        List<WishlistItemResponse> items = wishlist.getWishlistItems()
                .stream()
                .map(item -> toWishlistItemResponse(item, productThumbnails, variantImages))
                .toList();

        return WishlistResponse.builder()
                .wishlistId(wishlist.getId())
                .items(items)
                .totalItems(items.size())
                .build();
    }

}
