package com.stylenest.stylenest_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.ProductImage;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.repository.projection.VariantImageProjection;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {


    List<ProductImage> findByVariantOrderByDisplayOrderAsc(ProductVariant variant);

    /**
     * For each requested variant, the imageUrl of its own lowest-displayOrder
     * image. Batched into one query (no N+1) for callers resolving images
     * across several variants at once (e.g. wishlist items).
     */
    @Query(value = """
            SELECT ranked.variant_id AS variantId, ranked.image_url AS imageUrl
            FROM (
                SELECT
                    pi.variant_id AS variant_id,
                    pi.image_url AS image_url,
                    ROW_NUMBER() OVER (
                        PARTITION BY pi.variant_id
                        ORDER BY pi.display_order ASC
                    ) AS rn
                FROM product_images pi
                WHERE pi.variant_id IN (:variantIds)
            ) ranked
            WHERE ranked.rn = 1
            """, nativeQuery = true)
    List<VariantImageProjection> findFirstImageByVariantIds(@Param("variantIds") List<Long> variantIds);
}