package com.stylenest.stylenest_backend.dto.rental;

import java.time.LocalDate;
import java.util.List;

import lombok.*;

/** Every date within the current rental season that's already blocked for this item -- drives the customer calendar. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalUnavailableDatesResponse {

    private LocalDate seasonStartDate;
    private LocalDate seasonEndDate;
    private List<LocalDate> unavailableDates;
}
