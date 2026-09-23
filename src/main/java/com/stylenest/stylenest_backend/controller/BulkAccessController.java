package com.stylenest.stylenest_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stylenest.stylenest_backend.dto.bulk.BulkAccessValidateRequest;
import com.stylenest.stylenest_backend.dto.bulk.BulkAccessValidateResponse;
import com.stylenest.stylenest_backend.response.ApiResponse;
import com.stylenest.stylenest_backend.service.BulkTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The public "enter access code" gate. No auth of any kind is required to
 * call this -- it's the entry point precisely for customers who have no
 * account. Rate-limited per IP inside BulkTokenServiceImpl.
 */
@RestController
@RequestMapping("/api/bulk/access")
@RequiredArgsConstructor
public class BulkAccessController {

    private final BulkTokenService bulkTokenService;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<BulkAccessValidateResponse>> validate(
            @Valid @RequestBody BulkAccessValidateRequest request,
            HttpServletRequest httpRequest) {

        BulkAccessValidateResponse response =
                bulkTokenService.validateForCustomer(request.getAccessToken(), clientIp(httpRequest));

        return ResponseEntity.ok(ApiResponse.success("Access granted", response));
    }

    private String clientIp(HttpServletRequest request) {

        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
