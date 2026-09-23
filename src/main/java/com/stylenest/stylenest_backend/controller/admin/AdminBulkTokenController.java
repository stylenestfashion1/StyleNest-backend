package com.stylenest.stylenest_backend.controller.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.bulk.BulkAccessTokenSummaryResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.BulkTokenService;

import lombok.RequiredArgsConstructor;

/**
 * Permanent-token management -- no more one-time "generate a fresh 24h
 * code" flow. The 10 slots are provisioned once (at startup, and via
 * /provision as a manual safety-valve) and then only revoked, reactivated,
 * unassigned, or reissued.
 *
 * No explicit SecurityConfig matcher needed -- /api/admin/** already
 * requires ROLE_ADMIN via the existing catch-all rule.
 */
@RestController
@RequestMapping("/api/admin/bulk/access-tokens")
@RequiredArgsConstructor
public class AdminBulkTokenController {

    private final BulkTokenService bulkTokenService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BulkAccessTokenSummaryResponse>>> listTokens() {

        return ResponseEntity.ok(
                ApiResponse.success("Access tokens fetched successfully", bulkTokenService.listTokens()));
    }

    @PostMapping("/provision")
    public ResponseEntity<ApiResponse<List<BulkAccessTokenSummaryResponse>>> provisionTokens() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Permanent access tokens provisioned",
                        bulkTokenService.ensureTenPermanentTokensProvisioned()));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeToken(@PathVariable Long id) {

        bulkTokenService.revokeToken(id);

        return ResponseEntity.ok(ApiResponse.success("Access token revoked", null));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<Void>> reactivateToken(@PathVariable Long id) {

        bulkTokenService.reactivateToken(id);

        return ResponseEntity.ok(ApiResponse.success("Access token reactivated", null));
    }

    @PostMapping("/{id}/unassign")
    public ResponseEntity<ApiResponse<Void>> unassignToken(@PathVariable Long id) {

        bulkTokenService.unassignToken(id);

        return ResponseEntity.ok(ApiResponse.success("Access token unassigned", null));
    }

    @PostMapping("/{id}/reissue")
    public ResponseEntity<ApiResponse<BulkAccessTokenSummaryResponse>> reissueToken(@PathVariable Long id) {

        return ResponseEntity.ok(
                ApiResponse.success("Access token reissued", bulkTokenService.reissueToken(id)));
    }
}
