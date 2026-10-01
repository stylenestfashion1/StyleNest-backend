package com.stylenest.stylenest_backend.dto.rental;

import java.time.LocalDate;

import lombok.*;

/**
 * Both fields null/absent = cancel the entire booking. Both present =
 * partial cancellation of just that sub-range (must fall fully inside an
 * existing active segment -- see RentalBookingServiceImpl.cancelBooking).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalCancelRequest {

    private LocalDate startDate;
    private LocalDate endDate;
}
