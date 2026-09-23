package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.bulk.BulkProductRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkProductResponse;

public interface BulkProductService {

    BulkProductResponse createProduct(BulkProductRequest request);

    List<BulkProductResponse> getAllProducts();

    List<BulkProductResponse> getActiveProducts(Long categoryId);

    BulkProductResponse getProductById(Long id);

    BulkProductResponse getActiveProductById(Long id);

    BulkProductResponse updateProduct(Long id, BulkProductRequest request);

    void deleteProduct(Long id);
}
