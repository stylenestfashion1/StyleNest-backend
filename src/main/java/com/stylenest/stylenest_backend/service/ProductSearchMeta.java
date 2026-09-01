package com.stylenest.stylenest_backend.service;

import java.util.List;

/**
 * Resolved per-product search/listing metadata: thumbnail image and the
 * distinct set of colors available across its variants.
 */
public record ProductSearchMeta(String thumbnailUrl, List<String> availableColors) {

    public static final ProductSearchMeta EMPTY = new ProductSearchMeta(null, List.of());
}
