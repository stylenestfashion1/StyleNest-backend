package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.dto.product.ProductJeansCodeResponse;
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

    @Query("SELECT p.sku FROM Product p WHERE p.sku IS NOT NULL")
    List<String> findAllSkus();

    // Admin-only jeans-code lookups (see AdminProductController /
    // ProductJeansCodeResponse). Deliberately not exposed anywhere on the
    // public product read path.
    boolean existsByJeansCodeIgnoreCase(String jeansCode);

    boolean existsByJeansCodeIgnoreCaseAndIdNot(String jeansCode, Long id);

    @Query("SELECT new com.stylenest.stylenest_backend.dto.product.ProductJeansCodeResponse(p.id, p.jeansCode) "
            + "FROM Product p WHERE p.id = :id")
    Optional<ProductJeansCodeResponse> findJeansCodeByProductId(@Param("id") Long id);

    @Query("SELECT new com.stylenest.stylenest_backend.dto.product.ProductJeansCodeResponse(p.id, p.jeansCode) "
            + "FROM Product p")
    List<ProductJeansCodeResponse> findAllJeansCodes();

    /**
     * One row per (product, variant) for each requested product: the
     * resolved thumbnail image (same value repeated across a product's
     * rows) and that variant's color -- callers aggregate the distinct
     * colors themselves (see ProductThumbnailResolver). Images are keyed
     * by (product, color) -- every size shares its color's photos -- so
     * the thumbnail is by default the lowest-displayOrder image of the
     * product's lowest (alphabetically) color that has any images. When a
     * color filter is active (a search filter), a variant matching it is
     * preferred for the thumbnail if that color has an image, falling
     * back to the same default otherwise -- this only affects
     * ordering/preference, never which products are matched.
     *
     * Deliberately no STRING_AGG/GROUP_CONCAT here: that function's name
     * (and separator syntax) differs between PostgreSQL and MySQL, and
     * this app must run against either. Everything else here (plain
     * joins, ROW_NUMBER() OVER, EXISTS) is standard SQL supported by
     * both, so no database-specific SQL exists in this query at all.
     *
     * The EXISTS check on the inner subquery is load-bearing, not
     * decorative: product_images rows are independent of
     * product_variants (keyed only by color, not variant_id -- see
     * ProductImage), so deleting every variant of a color (e.g. after
     * splitting it into its own product) does NOT delete that color's
     * images. Without this check, such a leftover/orphaned color's image
     * could still win the ROW_NUMBER() ranking below -- e.g. a stray
     * "BLACK" image outranking the product's real "RED" images purely
     * because "BLACK" sorts alphabetically before "RED" -- and get shown
     * as the thumbnail everywhere even though that color no longer has
     * any variant, is not visible in the admin color-group UI, and is
     * not purchasable. Restricting candidates to colors with at least
     * one live variant makes an orphaned color's images simply inert
     * (never selectable) instead of leaving them a silent trap.
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
                            CASE WHEN (:color IS NULL OR pi.color = :color)
                                 THEN 0 ELSE 1 END,
                            pi.color ASC, pi.display_order ASC
                    ) AS rn
                FROM products p
                JOIN product_images pi ON pi.product_id = p.id
                WHERE p.id IN (:productIds)
                  AND EXISTS (
                      SELECT 1 FROM product_variants pv2
                      WHERE pv2.product_id = pi.product_id
                        AND pv2.color = pi.color
                  )
            ) ranked ON ranked.product_id = pv.product_id AND ranked.rn = 1
            WHERE pv.product_id IN (:productIds)
            """, nativeQuery = true)
    List<ProductSearchMetaProjection> findProductSearchMetaByProductIds(
            @Param("productIds") List<Long> productIds,
            @Param("color") String color);

 }