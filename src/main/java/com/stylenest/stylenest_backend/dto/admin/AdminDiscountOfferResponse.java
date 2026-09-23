package com.stylenest.stylenest_backend.dto.admin;

import java.time.LocalDateTime;

import lombok.*;

/** Admin-only -- mobileNumber is shown in full (the shopkeeper needs it to identify/contact the customer). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDiscountOfferResponse {

    private Long id;
    private String customerName;
    private String mobileNumber;
    private Integer discountPercentage;
    private String status;
    private LocalDateTime generatedAt;
}
