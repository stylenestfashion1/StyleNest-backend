package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.bulk.BulkAccessTokenSummaryResponse;
import com.stylenest.stylenest_backend.entity.BulkAccessToken;

@Component
public class BulkAccessTokenMapper {

    public BulkAccessTokenSummaryResponse toSummaryResponse(BulkAccessToken token) {

        String status = Boolean.TRUE.equals(token.getActive()) ? "ACTIVE" : "REVOKED";

        return BulkAccessTokenSummaryResponse.builder()
                .id(token.getId())
                .token(token.getToken())
                .status(status)
                .createdAt(token.getCreatedAt())
                .revokedAt(token.getRevokedAt())
                .assignedCustomerName(token.getAssignedCustomerName())
                .assignedCustomerEmail(token.getAssignedCustomerEmail())
                .assignedCustomerPhone(token.getAssignedCustomerPhone())
                .firstUsedAt(token.getFirstUsedAt())
                .lastUsedAt(token.getLastUsedAt())
                .createdByAdminEmail(token.getCreatedByAdmin() != null ? token.getCreatedByAdmin().getEmail() : null)
                .build();
    }
}
