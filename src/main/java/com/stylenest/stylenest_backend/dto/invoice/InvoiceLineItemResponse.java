package com.stylenest.stylenest_backend.dto.invoice;

import java.math.BigDecimal;

import lombok.*;

/** One row of the GST invoice item table -- see Invoice/InvoiceItem entities for field meanings. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceLineItemResponse {

    private String productName;
    private String variantInfo;
    private String hsnCode;
    private BigDecimal mrpPerUnit;
    private Integer quantity;
    private String unit;
    private BigDecimal pricePerUnit;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal gstRate;
    private BigDecimal gstAmount;
    private BigDecimal taxableAmount;
    private BigDecimal lineTotal;
}
