package com.stylenest.stylenest_backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.product.ProductImageRequest;
import com.stylenest.stylenest_backend.dto.product.ProductImageResponse;
import com.stylenest.stylenest_backend.entity.ProductImage;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.ProductImageMapper;
import com.stylenest.stylenest_backend.repository.ProductImageRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.service.ImageStorageService;
import com.stylenest.stylenest_backend.service.ProductImageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final ProductImageMapper imageMapper;
    private final ImageStorageService imageStorageService;

    @Override
    public ProductImageResponse addImage(Long variantId,
                                         ProductImageRequest request) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Variant not found with id: " + variantId));

        ProductImage image = imageMapper.toEntity(request);
        image.setVariant(variant);

        ProductImage savedImage = imageRepository.save(image);

        return imageMapper.toResponse(savedImage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImagesByVariant(Long variantId) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Variant not found with id: " + variantId));

        return imageRepository.findByVariantOrderByDisplayOrderAsc(variant)
                .stream()
                .map(imageMapper::toResponse)
                .toList();
    }

    @Override
    public void deleteImage(Long imageId) {

        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Image not found with id: " + imageId));

        imageRepository.delete(image);

        // No-op for externally hosted URLs -- only deletes a file that
        // actually lives in our own configured upload directory.
        imageStorageService.deleteIfManaged(image.getImageUrl());
    }
}