package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.entity.Order;

/**
 * Generates an invoice view/PDF from an already-loaded, already-authorized
 * Order. Does no auth/lookup of its own -- callers (customer/admin/guest
 * paths) each do their own ownership check first.
 */
public interface InvoiceService {

    /** In-memory PDF bytes only -- never touches the filesystem. */
    byte[] generateInvoicePdf(Order order);

    InvoiceResponse buildInvoiceView(Order order);
}
