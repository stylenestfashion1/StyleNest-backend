package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.invoice.InvoiceResponse;
import com.stylenest.stylenest_backend.entity.Invoice;

/**
 * Renders an already-generated Invoice (see InvoiceGenerationService) as a
 * JSON view or a PDF. Does no auth/lookup of its own -- callers
 * (customer/admin/guest paths) each do their own ownership check first.
 */
public interface InvoiceService {

    /** In-memory PDF bytes only -- never touches the filesystem. */
    byte[] generatePdf(Invoice invoice);

    InvoiceResponse buildView(Invoice invoice);
}
