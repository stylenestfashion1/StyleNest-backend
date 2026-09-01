package com.stylenest.stylenest_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.Wishlist;
import com.stylenest.stylenest_backend.entity.WishlistItem;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByWishlistAndProduct(
            Wishlist wishlist,
            Product product);

    /**
     * The specific-variant entry for this wishlist, if the customer
     * already saved this exact variant.
     */
    Optional<WishlistItem> findByWishlistAndProductVariant(
            Wishlist wishlist,
            ProductVariant productVariant);

    /**
     * The generic (no-variant) entry for this product in this wishlist,
     * if any -- separate from any variant-specific entries for the same
     * product.
     */
    Optional<WishlistItem> findByWishlistAndProductAndProductVariantIsNull(
            Wishlist wishlist,
            Product product);

    /**
     * Removes wishlist entries referencing the given product. Wishlists
     * are mutable customer preferences, not historical records, so this
     * is safe to do as part of deleting a product that has no order
     * history.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM WishlistItem w WHERE w.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

}