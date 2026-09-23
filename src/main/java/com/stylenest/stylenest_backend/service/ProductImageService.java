package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.product.ProductImageRequest;
import com.stylenest.stylenest_backend.dto.product.ProductImageResponse;

public interface ProductImageService {

    ProductImageResponse addImage(Long productId, String color, ProductImageRequest request);

    List<ProductImageResponse> getImagesByProductAndColor(Long productId, String color);

    void deleteImage(Long imageId);

    /**
     * Persists a new front-to-back display order for a color's images in
     * one shot, replacing the old delete-and-recreate workaround the
     * frontend used when no such endpoint existed (see
     * ImageUploadManager.jsx history) -- that approach could leave a color
     * with fewer images than it started with if a delete succeeded but the
     * matching re-add failed.
     */
    List<ProductImageResponse> reorderImages(Long productId, String color, List<Long> orderedImageIds);

}
