package com.stylenest.stylenest_backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.product.ProductVariantRequest;
import com.stylenest.stylenest_backend.dto.product.ProductVariantResponse;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.exception.VariantAlreadyExistsException;
import com.stylenest.stylenest_backend.mapper.ProductVariantMapper;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.service.ProductVariantService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductVariantMapper variantMapper;

    @Override
    public ProductVariantResponse createVariant(Long productId, ProductVariantRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + productId));

        if (request.getStock() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }

        if (variantRepository.existsByProductAndColorAndSize(
                product,
                request.getColor(),
                request.getSize())) {

            throw new VariantAlreadyExistsException(
                    "Variant already exists for this product.");
        }

        ProductVariant variant = variantMapper.toEntity(request);
        variant.setProduct(product);

        ProductVariant savedVariant = variantRepository.save(variant);

        return variantMapper.toResponse(savedVariant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getVariantsByProduct(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + productId));

        return variantRepository.findByProduct(product)
                .stream()
                .map(variantMapper::toResponse)
                .toList();
    }

    @Override
    public ProductVariantResponse updateVariant(Long variantId,
                                                ProductVariantRequest request) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Variant not found with id: " + variantId));

        if (request.getStock() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }

        if ((!variant.getColor().equals(request.getColor())
                || !variant.getSize().equals(request.getSize()))
                && variantRepository.existsByProductAndColorAndSize(
                        variant.getProduct(),
                        request.getColor(),
                        request.getSize())) {

            throw new VariantAlreadyExistsException(
                    "Variant already exists for this product.");
        }

        variantMapper.updateEntity(variant, request);

        ProductVariant updatedVariant = variantRepository.save(variant);

        return variantMapper.toResponse(updatedVariant);
    }

    @Override
    public void deleteVariant(Long variantId) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Variant not found with id: " + variantId));

        variantRepository.delete(variant);
    }
}