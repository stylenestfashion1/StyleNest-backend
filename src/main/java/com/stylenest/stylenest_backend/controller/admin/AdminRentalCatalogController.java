package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.rental.RentalCatalogItemRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogItemResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCatalogSummaryResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.RentalCatalogService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * No explicit SecurityConfig matcher needed: everything under
 * /api/admin/** already requires ROLE_ADMIN via the existing catch-all
 * rule (see SecurityConfig). Only the public GET-by-share-token endpoint,
 * on RentalCatalogController, needed a new permitAll line.
 */
@RestController
@RequestMapping("/api/admin/rental-catalogs")
@RequiredArgsConstructor
public class AdminRentalCatalogController {

    private final RentalCatalogService rentalCatalogService;

    @PostMapping
    public ResponseEntity<ApiResponse<RentalCatalogResponse>> createCatalog(
            @Valid @RequestBody RentalCatalogRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rental catalog created", rentalCatalogService.createCatalog(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RentalCatalogSummaryResponse>>> getAllCatalogs() {

        return ResponseEntity.ok(
                ApiResponse.success("Rental catalogs fetched", rentalCatalogService.getAllCatalogs()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RentalCatalogResponse>> getCatalogById(@PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Rental catalog fetched", rentalCatalogService.getCatalogById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RentalCatalogResponse>> renameCatalog(
            @PathVariable Long id, @Valid @RequestBody RentalCatalogRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Rental catalog updated", rentalCatalogService.renameCatalog(id, request)));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<RentalCatalogResponse>> activateCatalog(@PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Rental catalog activated", rentalCatalogService.activateCatalog(id)));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<RentalCatalogResponse>> deactivateCatalog(@PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Rental catalog deactivated", rentalCatalogService.deactivateCatalog(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCatalog(@PathVariable Long id) {

        rentalCatalogService.deleteCatalog(id);

        return ResponseEntity.ok(ApiResponse.success("Rental catalog deleted", null));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<RentalCatalogItemResponse>> addItem(
            @PathVariable Long id, @Valid @RequestBody RentalCatalogItemRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lehenga added", rentalCatalogService.addItem(id, request)));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<RentalCatalogItemResponse>> updateItem(
            @PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody RentalCatalogItemRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Lehenga updated", rentalCatalogService.updateItem(id, itemId, request)));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {

        rentalCatalogService.deleteItem(id, itemId);

        return ResponseEntity.ok(ApiResponse.success("Lehenga deleted", null));
    }

    @PutMapping("/{id}/items/reorder")
    public ResponseEntity<ApiResponse<RentalCatalogResponse>> reorderItems(
            @PathVariable Long id, @RequestBody List<Long> orderedItemIds) {

        return ResponseEntity.ok(
                ApiResponse.success("Lehengas reordered", rentalCatalogService.reorderItems(id, orderedItemIds)));
    }
}
