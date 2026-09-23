package com.stylenest.stylenest_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.ProductImage;
import com.stylenest.stylenest_backend.repository.projection.ProductColorImageProjection;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdAndColorOrderByDisplayOrderAsc(Long productId, String color);

    /**
     * Every image for a product, across all its colors, ordered so callers
     * can group-by-color in memory in one pass. Used when listing a
     * product's variants -- every size of the same color shares this same
     * image set.
     */
    List<ProductImage> findByProductIdOrderByColorAscDisplayOrderAsc(Long productId);

    /**
     * For each requested product, the imageUrl of the lowest-displayOrder
     * image of each color it has. Batched into one query (no N+1) for
     * callers resolving images across several products at once (e.g.
     * wishlist items).
     */
    @Query(value = """
            SELECT ranked.product_id AS productId, ranked.color AS color, ranked.image_url AS imageUrl
            FROM (
                SELECT
                    pi.product_id AS product_id,
                    pi.color AS color,
                    pi.image_url AS image_url,
                    ROW_NUMBER() OVER (
                        PARTITION BY pi.product_id, pi.color
                        ORDER BY pi.display_order ASC
                    ) AS rn
                FROM product_images pi
                WHERE pi.product_id IN (:productIds)
            ) ranked
            WHERE ranked.rn = 1
            """, nativeQuery = true)
    List<ProductColorImageProjection> findFirstImageByProductIdsGroupedByColor(@Param("productIds") List<Long> productIds);
}
