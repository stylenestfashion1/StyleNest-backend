package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.entity.Order;

/**
 * Service for dispatching server-side tracking events to Meta (Facebook)
 * Conversions API (CAPI) for ads optimization and deduplication with browser Pixel.
 */
public interface MetaCapiService {

    /**
     * Dispatches a server-side Purchase event to Meta Graph API.
     * Executes asynchronously and fails silently if CAPI is unconfigured or unavailable.
     *
     * @param order the confirmed retail order
     */
    void sendPurchaseEvent(Order order);
}
