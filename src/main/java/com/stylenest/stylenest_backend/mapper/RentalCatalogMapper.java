package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.rental.RentalCatalogItemPublicResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogItemResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogPublicResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogSummaryResponse;
import com.stylenest.stylenest_backend.entity.RentalCatalog;
import com.stylenest.stylenest_backend.entity.RentalCatalogItem;
import com.stylenest.stylenest_backend.entity.RentalCatalogItemImage;

@Component
public class RentalCatalogMapper {

    public RentalCatalogResponse toResponse(RentalCatalog catalog) {

        return RentalCatalogResponse.builder()
                .id(catalog.getId())
                .name(catalog.getName())
                .shareToken(catalog.getShareToken())
                .status(catalog.getStatus())
                .items(catalog.getItems().stream().map(this::toItemResponse).toList())
                .createdAt(catalog.getCreatedAt())
                .updatedAt(catalog.getUpdatedAt())
                .build();
    }

    public RentalCatalogSummaryResponse toSummaryResponse(RentalCatalog catalog) {

        return RentalCatalogSummaryResponse.builder()
                .id(catalog.getId())
                .name(catalog.getName())
                .shareToken(catalog.getShareToken())
                .status(catalog.getStatus())
                .itemCount(catalog.getItems().size())
                .createdAt(catalog.getCreatedAt())
                .build();
    }

    public RentalCatalogItemResponse toItemResponse(RentalCatalogItem item) {

        return RentalCatalogItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .colour(item.getColour())
                .rentalPrice(item.getRentalPrice())
                .displayOrder(item.getDisplayOrder())
                .imageUrls(item.getImages().stream().map(RentalCatalogItemImage::getImageUrl).toList())
                .build();
    }

    public RentalCatalogPublicResponse toPublicResponse(RentalCatalog catalog) {

        return RentalCatalogPublicResponse.builder()
                .name(catalog.getName())
                .items(catalog.getItems().stream().map(this::toItemPublicResponse).toList())
                .build();
    }

    private RentalCatalogItemPublicResponse toItemPublicResponse(RentalCatalogItem item) {

        return RentalCatalogItemPublicResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .colour(item.getColour())
                .rentalPrice(item.getRentalPrice())
                .imageUrls(item.getImages().stream().map(RentalCatalogItemImage::getImageUrl).toList())
                .build();
    }
}
