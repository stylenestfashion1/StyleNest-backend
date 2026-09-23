package com.stylenest.stylenest_backend.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.wishlist.AddToWishlistRequest;
import com.stylenest.stylenest_backend.dto.wishlist.WishlistResponse;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.entity.Wishlist;
import com.stylenest.stylenest_backend.entity.WishlistItem;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.WishlistMapper;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.repository.WishlistItemRepository;
import com.stylenest.stylenest_backend.repository.WishlistRepository;
import com.stylenest.stylenest_backend.service.ProductThumbnailResolver;
import com.stylenest.stylenest_backend.service.VariantImageResolver;
import com.stylenest.stylenest_backend.service.WishlistService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final WishlistMapper wishlistMapper;
    private final ProductThumbnailResolver thumbnailResolver;
    private final VariantImageResolver variantImageResolver;

    private User getCurrentUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found."));
    }

    private Wishlist getOrCreateWishlist(User user) {

        return wishlistRepository.findByUser(user)
                .orElseGet(() -> {

                    Wishlist wishlist = Wishlist.builder()
                            .user(user)
                            .wishlistItems(new ArrayList<>())
                            .build();

                    return wishlistRepository.save(wishlist);

                });
    }

    /**
     * Resolves the variant referenced by the request, if any, validating
     * that it belongs to the given product. A mismatched pair is rejected
     * outright rather than silently ignored, so a frontend bug surfaces
     * immediately instead of storing an inconsistent reference.
     */
    private ProductVariant resolveAndValidateVariant(Product product, Long productVariantId) {

        if (productVariantId == null) {
            return null;
        }

        ProductVariant variant = productVariantRepository.findById(productVariantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product Variant not found with id: " + productVariantId));

        if (!variant.getProduct().getId().equals(product.getId())) {
            throw new BadRequestException(
                    "The specified variant does not belong to the specified product.");
        }

        return variant;
    }

    @Override
    public WishlistResponse addToWishlist(AddToWishlistRequest request) {

        User user = getCurrentUser();

        Wishlist wishlist = getOrCreateWishlist(user);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found."));

        ProductVariant variant = resolveAndValidateVariant(product, request.getProductVariantId());

        // Uniqueness is variant-aware: a specific variant (e.g. RED/L) and
        // the generic no-variant entry for the same product are separate,
        // independent wishlist items. Adding a different variant of a
        // product that's already wishlisted (in some other variant, or
        // generically) creates a NEW item rather than replacing anything.
        Optional<WishlistItem> existing = variant != null
                ? wishlistItemRepository.findByWishlistAndProductVariant(wishlist, variant)
                : wishlistItemRepository.findByWishlistAndProductAndProductVariantIsNull(wishlist, product);

        if (existing.isPresent()) {
            // Exact same variant (or exact same no-variant product)
            // already saved -- idempotent, no duplicate.
            return toResponse(wishlist);
        }

        WishlistItem wishlistItem = WishlistItem.builder()
                .wishlist(wishlist)
                .product(product)
                .productVariant(variant)
                .build();

        wishlistItemRepository.save(wishlistItem);

        wishlist.getWishlistItems().add(wishlistItem);

        return toResponse(wishlist);
    }

    @Override
    public WishlistResponse getWishlist() {

        User user = getCurrentUser();

        Wishlist wishlist = getOrCreateWishlist(user);

        return toResponse(wishlist);
    }

    private WishlistResponse toResponse(Wishlist wishlist) {

        List<WishlistItem> items = wishlist.getWishlistItems();

        List<Long> productIds = items.stream()
                .filter(item -> item.getProductVariant() == null)
                .map(item -> item.getProduct().getId())
                .toList();

        List<ProductVariant> variantsWithImages = items.stream()
                .map(WishlistItem::getProductVariant)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, String> productThumbnails = thumbnailResolver.resolve(productIds);
        Map<Long, String> variantImages = variantImageResolver.resolve(variantsWithImages);

        return wishlistMapper.toWishlistResponse(wishlist, productThumbnails, variantImages);
    }

    @Override
    public void removeItem(Long wishlistItemId) {

        User currentUser = getCurrentUser();

        WishlistItem wishlistItem = wishlistItemRepository.findById(wishlistItemId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Wishlist Item not found."));

        if (!wishlistItem.getWishlist().getUser().getId()
                .equals(currentUser.getId())) {

            throw new ResourceNotFoundException(
                    "Wishlist Item not found.");
        }

        // Keep the parent's in-memory collection in sync (mirrors how
        // addToWishlist explicitly appends), not just the DB row --
        // otherwise an already-loaded Wishlist held elsewhere in the same
        // persistence context would still show the deleted item.
        wishlistItem.getWishlist().getWishlistItems().remove(wishlistItem);

        wishlistItemRepository.delete(wishlistItem);
    }

    @Override
    public void clearWishlist() {

        User user = getCurrentUser();

        Wishlist wishlist = getOrCreateWishlist(user);

        wishlist.getWishlistItems().clear();

        wishlistRepository.save(wishlist);
    }
}
