package com.stylenest.stylenest_backend.dto.invoice;

import java.math.BigDecimal;

import lombok.*;

/**
 * The simple line-item shape used only by the order-confirmation EMAIL
 * template (EmailServiceImpl) -- kept exactly as it was so that existing,
 * already-working code is untouched. The new GST-aware invoice view/PDF
 * uses {@link InvoiceLineItemResponse} instead; the two are deliberately
 * separate DTOs even though both ultimately describe "one product line."
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItemResponse {

    private String productName;

    private String color;

    private String size;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal subtotal;
}
