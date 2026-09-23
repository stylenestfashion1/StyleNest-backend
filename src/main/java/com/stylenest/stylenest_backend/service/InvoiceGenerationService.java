package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.entity.BulkOrder;
import com.stylenest.stylenest_backend.entity.Invoice;
import com.stylenest.stylenest_backend.entity.Order;

/**
 * Creates and persists the one-time Invoice snapshot for a finalized order.
 * Idempotent: calling this twice for the same order returns the existing
 * invoice rather than creating a duplicate or changing anything.
 */
public interface InvoiceGenerationService {

    Invoice generateForRetailOrder(Order order);

    Invoice generateForBulkOrder(BulkOrder bulkOrder);
}
