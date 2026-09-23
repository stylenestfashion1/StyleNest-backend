package com.stylenest.stylenest_backend.dto.invoice;

import java.math.BigDecimal;

import lombok.*;

/** One row of the invoice's tax-type breakdown table, grouped by GST rate. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceTaxBreakupResponse {

    private String taxType; // CGST | SGST | IGST
    private BigDecimal rate;
    private BigDecimal taxableAmount;
    private BigDecimal taxAmount;
}
