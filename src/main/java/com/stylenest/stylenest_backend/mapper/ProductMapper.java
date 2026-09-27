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
                .categoryName(product.getCategory().getName())
                .categorySlug(product.getCategory().getSlug())
                .gender(product.getCategory().getGender())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
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
    }
}