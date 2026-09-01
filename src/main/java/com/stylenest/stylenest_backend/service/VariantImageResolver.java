package com.stylenest.stylenest_backend.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.repository.ProductImageRepository;
import com.stylenest.stylenest_backend.repository.projection.VariantImageProjection;

import lombok.RequiredArgsConstructor;

/**
 * Resolves a specific variant's own lowest-displayOrder image -- the
 * per-variant counterpart to ProductThumbnailResolver's per-product
 * fallback. Batches the lookup into one query per call (no N+1).
 */
@Component
@RequiredArgsConstructor
public class VariantImageResolver {

    private final ProductImageRepository productImageRepository;

    public Map<Long, String> resolve(List<Long> variantIds) {

        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctIds = variantIds.stream().distinct().toList();

        return productImageRepository.findFirstImageByVariantIds(distinctIds)
                .stream()
                .collect(Collectors.toMap(
                        VariantImageProjection::getVariantId,
                        VariantImageProjection::getImageUrl));
    }
}
