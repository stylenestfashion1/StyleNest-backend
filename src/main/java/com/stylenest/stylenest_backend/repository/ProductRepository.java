package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Category;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.repository.projection.ProductSearchMetaProjection;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>,
JpaSpecificationExecutor<Product> {

    List<Product> findByCategory(Category category);

    boolean existsByCategory(Category category);

    List<Product> findByNameContainingIgnoreCase(String keyword);

    List<Product> findByFeaturedTrue();

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * One row per (product, variant) for each requested product: the
     * resolved thumbnail image (same value repeated across a product's
     * rows) and that variant's color -- callers aggregate the distinct
     * colors themselves (see ProductThumbnailResolver). By default the
     * thumbnail is the lowest-displayOrder image of the lowest-id variant
     * that has any images. When color/size are given (an active search
     * filter), a variant matching them is preferred for the thumbnail if
     * it has an image, falling back to the same default otherwise -- this
     * only affects ordering/preference, never which products are matched.
     *
     * Deliberately no STRING_AGG/GROUP_CONCAT here: that function's name
     * (and separator syntax) differs between PostgreSQL and MySQL, and
     * this app must run against either. Everything else here (plain
     * joins, ROW_NUMBER() OVER) is standard SQL supported by both, so no
     * database-specific SQL exists in this query at all.
     */
    @Query(value = """
            SELECT
                pv.product_id AS productId,
                ranked.thumbnail_url AS thumbnailUrl,
                pv.color AS color
            FROM product_variants pv
            LEFT JOIN (
                SELECT
                    p.id AS product_id,
                    pi.image_url AS thumbnail_url,
                    ROW_NUMBER() OVER (
                        PARTITION BY p.id
                        ORDER BY
                            CASE WHEN (:color IS NULL OR rpv.color = :color)
                                 AND (:size IS NULL OR rpv.size = :size)
                                 THEN 0 ELSE 1 END,
                            rpv.id ASC, pi.display_order ASC
                    ) AS rn
                FROM products p
                JOIN product_variants rpv ON rpv.product_id = p.id
                JOIN product_images pi ON pi.variant_id = rpv.id
                WHERE p.id IN (:productIds)
            ) ranked ON ranked.product_id = pv.product_id AND ranked.rn = 1
            WHERE pv.product_id IN (:productIds)
            """, nativeQuery = true)
    List<ProductSearchMetaProjection> findProductSearchMetaByProductIds(
            @Param("productIds") List<Long> productIds,
            @Param("color") String color,
            @Param("size") String size);

 }