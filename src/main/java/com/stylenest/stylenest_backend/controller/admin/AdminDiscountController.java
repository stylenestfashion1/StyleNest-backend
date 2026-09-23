package com.stylenest.stylenest_backend.controller.admin;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.admin.AdminDiscountOfferResponse;
import com.stylenest.stylenest_backend.dto.admin.AdminDiscountOfferSearchRequest;
import com.stylenest.stylenest_backend.dto.admin.AdminDiscountQrResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountConfigResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountConfigUpdateRequest;
import com.stylenest.stylenest_backend.dto.discount.DiscountRangeResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountRangeUpdateRequest;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.DiscountConfigService;
import com.stylenest.stylenest_backend.service.DiscountOfferService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin-only (ROLE_ADMIN, enforced by the existing /api/admin/** rule in
 * SecurityConfig -- no route-specific security change needed here).
 * Backs Admin Panel -> Rewards -> Custom Discount / Redeemed Offers /
 * Show Discount QR.
 */
@Tag(name = "Discount / Rewards")
@RestController
@RequestMapping("/api/admin/discount")
@RequiredArgsConstructor
public class AdminDiscountController {

    private final DiscountConfigService discountConfigService;
    private final DiscountOfferService discountOfferService;

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<DiscountConfigResponse>> getConfig() {

        return ResponseEntity.ok(
                ApiResponse.success("Discount configuration fetched", discountConfigService.getConfig()));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<DiscountConfigResponse>> updateConfig(
            @Valid @RequestBody DiscountConfigUpdateRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Discount configuration updated", discountConfigService.updateConfig(request)));
    }

    // Updates ONLY the QR poster's display text (e.g. "15% - 40% OFF") --
    // completely independent of /config's slots, which are the sole
    // authority on the actual discount-generation logic. Neither endpoint
    // reads or writes the other's data. See DiscountConfigServiceImpl javadoc.
    @PutMapping("/range")
    public ResponseEntity<ApiResponse<DiscountRangeResponse>> updateRange(
            @Valid @RequestBody DiscountRangeUpdateRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("QR display range updated", discountConfigService.updateRange(request)));
    }

    @GetMapping("/offers")
    public ResponseEntity<ApiResponse<Page<AdminDiscountOfferResponse>>> searchOffers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer discountPercentage,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "generatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        AdminDiscountOfferSearchRequest request = AdminDiscountOfferSearchRequest.builder()
                .keyword(keyword)
                .discountPercentage(discountPercentage)
                .fromDate(fromDate)
                .toDate(toDate)
                .page(page)
                .sizePerPage(size)
                .sortBy(sortBy)
                .direction(direction)
                .build();

        return ResponseEntity.ok(
                ApiResponse.success("Redeemed offers fetched", discountOfferService.searchOffers(request)));
    }

    @GetMapping("/qr")
    public ResponseEntity<ApiResponse<AdminDiscountQrResponse>> getQr() {

        return ResponseEntity.ok(
                ApiResponse.success("QR info fetched", discountOfferService.getQrInfo()));
    }
}
