package com.stylenest.stylenest_backend.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.product.ProductImageRequest;
import com.stylenest.stylenest_backend.dto.product.ProductImageResponse;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductImage;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.ProductImageMapper;
import com.stylenest.stylenest_backend.repository.ProductImageRepository;
import com.stylenest.stylenest_backend.repository.ProductRepository;
import com.stylenest.stylenest_backend.service.ImageStorageService;
import com.stylenest.stylenest_backend.service.ProductImageService;
import com.stylenest.stylenest_backend.util.ColorNormalizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductImageMapper imageMapper;
    private final ImageStorageService imageStorageService;

    @Override
    public ProductImageResponse addImage(Long productId, String color, ProductImageRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + productId));

        ProductImage image = imageMapper.toEntity(request);
        image.setProduct(product);
        image.setColor(ColorNormalizer.normalize(color));

        ProductImage savedImage = imageRepository.save(image);

        return imageMapper.toResponse(savedImage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImagesByProductAndColor(Long productId, String color) {

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        return imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(
                        productId, ColorNormalizer.normalize(color))
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

    @Override
    public List<ProductImageResponse> reorderImages(Long productId, String color, List<Long> orderedImageIds) {

        List<ProductImage> images =
                imageRepository.findByProductIdAndColorOrderByDisplayOrderAsc(
                        productId, ColorNormalizer.normalize(color));

        Map<Long, ProductImage> byId = images.stream()
                .collect(Collectors.toMap(ProductImage::getId, img -> img));

        if (orderedImageIds == null
                || orderedImageIds.size() != images.size()
                || !byId.keySet().containsAll(orderedImageIds)
                || orderedImageIds.stream().distinct().count() != orderedImageIds.size()) {

            throw new BadRequestException(
                    "orderedImageIds must contain exactly the current image ids for this color, each once.");
        }

        for (int i = 0; i < orderedImageIds.size(); i++) {
            byId.get(orderedImageIds.get(i)).setDisplayOrder(i);
        }

        return imageRepository.saveAll(images)
                .stream()
                .sorted((a, b) -> a.getDisplayOrder().compareTo(b.getDisplayOrder()))
                .map(imageMapper::toResponse)
                .toList();
    }
}
