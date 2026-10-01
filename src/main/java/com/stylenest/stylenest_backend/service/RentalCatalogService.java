package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.rental.RentalActiveCatalogResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogItemRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogItemResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogPublicResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogSummaryResponse;

public interface RentalCatalogService {

    // -- Admin --

    RentalCatalogResponse createCatalog(RentalCatalogRequest request);

    List<RentalCatalogSummaryResponse> getAllCatalogs();

    RentalCatalogResponse getCatalogById(Long id);

    RentalCatalogResponse renameCatalog(Long id, RentalCatalogRequest request);

    RentalCatalogResponse activateCatalog(Long id);

    RentalCatalogResponse deactivateCatalog(Long id);

    void deleteCatalog(Long id);

    RentalCatalogItemResponse addItem(Long catalogId, RentalCatalogItemRequest request);

    RentalCatalogItemResponse updateItem(Long catalogId, Long itemId, RentalCatalogItemRequest request);

    void deleteItem(Long catalogId, Long itemId);

    RentalCatalogResponse reorderItems(Long catalogId, List<Long> orderedItemIds);

    // -- Public --

    /** Throws ResourceNotFoundException for an unknown or INACTIVE token -- callers never distinguish the two. */
    RentalCatalogPublicResponse getPublicCatalogByShareToken(String shareToken);

    /** Never throws -- {@code available=false} is a normal, expected state (no ACTIVE catalog right now). */
    RentalActiveCatalogResponse getActiveCatalog();
}
