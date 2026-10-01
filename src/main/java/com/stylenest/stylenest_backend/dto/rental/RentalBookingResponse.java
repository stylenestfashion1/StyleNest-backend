package com.stylenest.stylenest_backend.dto.rental;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.RentalBookingStatus;

import lombok.*;

/** Shared shape for both the customer's own booking confirmation and the admin detail view. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalBookingResponse {

    private String bookingReference;
    private Long itemId;
    private String itemName;
    private String itemColour;
    private String itemImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer rentalDays;
    private BigDecimal dailyRate;
    private BigDecimal totalAmount;
    private RentalBookingStatus status;
    private String customerName;
    private String customerPhone;
    private String transactionId;
    private String paymentScreenshotUrl;
    private LocalDateTime createdAt;
}
