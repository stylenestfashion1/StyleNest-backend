package com.stylenest.stylenest_backend.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.product.ProductPriceResponse;
import com.stylenest.stylenest_backend.dto.product.ProductRequest;
import com.stylenest.stylenest_backend.dto.product.ProductResponse;
import com.stylenest.stylenest_backend.entity.Product;
import com.stylenest.stylenest_backend.entity.ProductPrice;
import com.stylenest.stylenest_backend.service.ProductSearchMeta;

@Component
public class ProductMapper {

    public Product toEntity(ProductRequest request) {

        return Product.builder()
                .name(request.getName())
                .shortDescription(request.getShortDescription())
                .description(request.getDescription())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .fabric(request.getFabric())
                .careInstructions(request.getCareInstructions())
                .hsnCode(request.getHsnCode())
                .jeansCode(normalizeJeansCode(request.getJeansCode()))
                .featured(request.getFeatured())
                .trending(request.getTrending())
                .active(request.getActive())
                .shippingWeightGrams(request.getShippingWeightGrams())
                .packageLengthCm(request.getPackageLengthCm())
                .packageWidthCm(request.getPackageWidthCm())
                .packageHeightCm(request.getPackageHeightCm())
                .build();
    }

    // Trims whitespace (the one explicitly-requested normalization) and
    // collapses blank to null so an empty field clears any existing code
    // rather than persisting "" -- never alters the code's actual
    // characters/casing otherwise.
    private String normalizeJeansCode(String jeansCode) {
        if (jeansCode == null) return null;
        String trimmed = jeansCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<ProductPriceResponse> toPriceResponses(List<ProductPrice> prices) {
        return prices.stream()
                .map(pp -> ProductPriceResponse.builder()
                        .currency(pp.getCurrency())
                        .regularPrice(pp.getRegularPrice())
                        .discountPrice(pp.getDiscountPrice())
                        .build())
                .toList();
    }

    public ProductResponse toResponse(Product product) {
        return toResponse(product, ProductSearchMeta.EMPTY);
    }

    public ProductResponse toResponse(Product product, ProductSearchMeta meta) {

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .sku(product.getSku())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .price(product.getPrice())
                .discountPrice(product.getDiscountPrice())
                .prices(toPriceResponses(product.getPrices()))
                .fabric(product.getFabric())
                .careInstructions(product.getCareInstructions())
                .hsnCode(product.getHsnCode())
                .featured(product.getFeatured())
                .trending(product.getTrending())
                .active(product.getActive())
                .thumbnailUrl(meta.thumbnailUrl())
                .availableColors(meta.availableColors())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .categorySlug(product.getCategory().getSlug())
                .gender(product.getCategory().getGender())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .shippingWeightGrams(product.getShippingWeightGrams())
                .packageLengthCm(product.getPackageLengthCm())
                .packageWidthCm(product.getPackageWidthCm())
                .packageHeightCm(product.getPackageHeightCm())
                .build();
    }

    public void updateEntity(Product product, ProductRequest request) {

        product.setName(request.getName());
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setDiscountPrice(request.getDiscountPrice());
        product.setFabric(request.getFabric());
        product.setCareInstructions(request.getCareInstructions());
        product.setHsnCode(request.getHsnCode());
        product.setJeansCode(normalizeJeansCode(request.getJeansCode()));
        product.setFeatured(request.getFeatured());
        product.setTrending(request.getTrending());
        product.setActive(request.getActive());
        product.setShippingWeightGrams(request.getShippingWeightGrams());
        product.setPackageLengthCm(request.getPackageLengthCm());
        product.setPackageWidthCm(request.getPackageWidthCm());
        product.setPackageHeightCm(request.getPackageHeightCm());
    }
}