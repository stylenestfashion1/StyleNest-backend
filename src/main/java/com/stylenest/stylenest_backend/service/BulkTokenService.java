package com.stylenest.stylenest_backend.service;

import java.util.List;

import com.stylenest.stylenest_backend.dto.bulk.BulkAccessTokenSummaryResponse;
import com.stylenest.stylenest_backend.dto.bulk.BulkAccessValidateResponse;
import com.stylenest.stylenest_backend.entity.BulkAccessToken;

public interface BulkTokenService {

    /**
     * Idempotent: creates only as many new permanent tokens as needed to
     * bring the total up to 10 (never touches existing ones, never removes
     * any). Safe to call repeatedly -- at startup, and on-demand from the
     * admin "Provision" action.
     */
    List<BulkAccessTokenSummaryResponse> ensureTenPermanentTokensProvisioned();

    List<BulkAccessTokenSummaryResponse> listTokens();

    void revokeToken(Long id);

    void reactivateToken(Long id);

    void unassignToken(Long id);

    /** Replaces the slot's secret value with a freshly generated one and clears its assignment. */
    BulkAccessTokenSummaryResponse reissueToken(Long id);

    /** Public, rate-limited entry point for the customer-facing "enter code" gate. */
    BulkAccessValidateResponse validateForCustomer(String plaintextToken, String clientIp);

    /**
     * Resolves and re-validates a token from its raw header value on every
     * bulk-customer API call (browsing, cart, checkout). Throws
     * BulkTokenInvalidException / BulkTokenRevokedException on failure --
     * never returns null. Does NOT check customer assignment -- that's
     * only enforced at order placement (see BulkOrderServiceImpl).
     */
    BulkAccessToken resolveActiveToken(String plaintextToken);
}
