package com.stylenest.stylenest_backend.service;

import java.util.List;

import org.springframework.data.domain.Page;

import com.stylenest.stylenest_backend.dto.product.ProductJeansCodeResponse;
import com.stylenest.stylenest_backend.dto.product.ProductRequest;
import com.stylenest.stylenest_backend.dto.product.ProductResponse;
import com.stylenest.stylenest_backend.dto.product.filter.ProductFilterRequest;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    List<ProductResponse> getAllProducts();

    ProductResponse getProductById(Long id);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);

    Page<ProductResponse> searchProducts(ProductFilterRequest request);

    // Admin-only -- see ProductJeansCodeResponse for why this is never
    // folded into ProductResponse.
    ProductJeansCodeResponse getJeansCode(Long id);

    List<ProductJeansCodeResponse> getAllJeansCodes();
}