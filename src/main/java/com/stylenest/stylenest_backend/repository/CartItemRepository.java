package com.stylenest.stylenest_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.CartItem;
import com.stylenest.stylenest_backend.entity.ProductVariant;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndProductVariant(
            Cart cart,
            ProductVariant productVariant);

    /**
     * Removes cart entries referencing any variant of the given product.
     * Carts are ephemeral/mutable, so this is safe to do as part of
     * deleting a product that has no order history.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CartItem c WHERE c.productVariant.product.id = :productId")
    void deleteByProductVariant_Product_Id(@Param("productId") Long productId);

}