package com.stylenest.stylenest_backend.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.repository.ProductImageRepository;
import com.stylenest.stylenest_backend.repository.projection.ProductColorImageProjection;

import lombok.RequiredArgsConstructor;

/**
 * Resolves a specific variant's image -- the per-variant counterpart to
 * ProductThumbnailResolver's per-product fallback. Images are keyed by
 * (product, color), not by an individual size variant, so every size of
 * the same color resolves to the same photo; this batches the lookup into
 * one query per call (no N+1).
 */
@Component
@RequiredArgsConstructor
public class VariantImageResolver {

    private final ProductImageRepository productImageRepository;

    public Map<Long, String> resolve(List<ProductVariant> variants) {

        if (variants == null || variants.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctProductIds = variants.stream()
                .map(v -> v.getProduct().getId())
                .distinct()
                .toList();

        Map<String, String> imageByProductAndColor = productImageRepository
                .findFirstImageByProductIdsGroupedByColor(distinctProductIds)
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getProductId() + ":" + p.getColor(),
                        ProductColorImageProjection::getImageUrl,
                        (a, b) -> a));

        Map<Long, String> result = new HashMap<>();
        for (ProductVariant variant : variants) {
            String key = variant.getProduct().getId() + ":" + variant.getColor();
            String imageUrl = imageByProductAndColor.get(key);
            if (imageUrl != null) {
                result.put(variant.getId(), imageUrl);
            }
        }
        return result;
    }
}
