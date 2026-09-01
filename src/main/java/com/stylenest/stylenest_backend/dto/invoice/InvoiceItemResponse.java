package com.stylenest.stylenest_backend.dto.invoice;

import java.math.BigDecimal;

import lombok.*;

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
