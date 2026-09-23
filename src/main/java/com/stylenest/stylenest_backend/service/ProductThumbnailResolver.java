package com.stylenest.stylenest_backend.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.projection.ProductSearchMetaProjection;

import lombok.RequiredArgsConstructor;

/**
 * Single source of truth for resolving a product's thumbnail image and
 * available colors: the lowest-displayOrder image of the product's
 * lowest-id variant that has any images (or, when a color/size filter is
 * active, preferring a variant matching it), plus the distinct colors
 * across all of its variants. Batches the lookup into one query per call
 * so callers (product listing, search, wishlist, ...) never do this
 * per-row.
 */
@Component
@RequiredArgsConstructor
public class ProductThumbnailResolver {

    private final ProductRepository productRepository;

    /**
     * Thumbnail-only lookup, unfiltered. Used by callers (e.g. wishlist's
     * product-level fallback) that only need the image URL.
     */
    public Map<Long, String> resolve(List<Long> productIds) {

        // Not Collectors.toMap: thumbnailUrl is routinely null (a product
        // with no images yet), and toMap's collector throws NPE on a null
        // value.
        Map<Long, String> thumbnails = new HashMap<>();

        for (Map.Entry<Long, ProductSearchMeta> entry : resolveMeta(productIds, null).entrySet()) {
            thumbnails.put(entry.getKey(), entry.getValue().thumbnailUrl());
        }

        return thumbnails;
    }

    /**
     * Full metadata lookup (thumbnail + available colors), optionally
     * preferring the thumbnail of a variant matching the given color
     * (e.g. an active search filter). Pass null to skip that preference.
     */
    public Map<Long, ProductSearchMeta> resolveMeta(
            List<Long> productIds, String color) {

        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctIds = productIds.stream().distinct().toList();

        String normalizedColor = normalize(color);

        Map<Long, ProductSearchMeta> result = distinctIds.stream()
                .collect(Collectors.toMap(id -> id, id -> ProductSearchMeta.EMPTY));

        // Query returns one row per (product, variant) rather than a
        // pre-aggregated color list (no STRING_AGG/GROUP_CONCAT -- see
        // ProductRepository for why) -- aggregate here instead, deduping
        // with a LinkedHashSet since a product can have several variants
        // sharing the same color (e.g. same color, different sizes).
        Map<Long, String> thumbnailByProduct = new HashMap<>();
        Map<Long, Set<String>> colorsByProduct = new HashMap<>();

        for (ProductSearchMetaProjection row : productRepository.findProductSearchMetaByProductIds(
                distinctIds, normalizedColor)) {

            thumbnailByProduct.putIfAbsent(row.getProductId(), row.getThumbnailUrl());

            if (row.getColor() != null) {
                colorsByProduct
                        .computeIfAbsent(row.getProductId(), id -> new LinkedHashSet<>())
                        .add(row.getColor());
            }
        }

        for (Long id : thumbnailByProduct.keySet()) {

            List<String> colors = colorsByProduct.containsKey(id)
                    ? List.copyOf(colorsByProduct.get(id))
                    : List.of();

            result.put(id, new ProductSearchMeta(thumbnailByProduct.get(id), colors));
        }

        return result;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.toUpperCase();
    }
}
