package com.stylenest.stylenest_backend.repository.projection;

/**
 * One row per (product, variant): the product's resolved thumbnail
 * (optionally preferring a variant matching an active color/size filter,
 * same value repeated across a product's rows) and that variant's color.
 * Callers aggregate the distinct colors per product themselves.
 */
public interface ProductSearchMetaProjection {

    Long getProductId();

    String getThumbnailUrl();

    String getColor();
}
