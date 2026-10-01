package com.stylenest.stylenest_backend.dto.rental;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stylenest.stylenest_backend.enums.RentalBookingStatus;

import lombok.*;

/** Row shape for Admin -> Rental Bookings. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalBookingSummaryResponse {

    private String bookingReference;
    private String itemName;
    private String customerName;
    private String customerPhone;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer rentalDays;
    private BigDecimal totalAmount;
    private RentalBookingStatus status;
}
