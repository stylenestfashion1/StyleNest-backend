package com.stylenest.stylenest_backend.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.rental.RentalBookingCreateRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalBookingResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalBookingSummaryResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCancelRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalUnavailableDatesResponse;

public interface RentalBookingService {

    // -- Public (share-token scoped) --

    RentalUnavailableDatesResponse getUnavailableDates(String shareToken, Long itemId);

    RentalBookingResponse createBooking(String shareToken, Long itemId, RentalBookingCreateRequest request);

    RentalBookingResponse submitPayment(
            String shareToken, String bookingReference, String transactionId, MultipartFile screenshot);

    // -- Admin --

    List<RentalBookingSummaryResponse> getAllBookings();

    RentalBookingResponse getBookingByReference(String bookingReference);

    RentalBookingResponse confirmBooking(String bookingReference);

    RentalBookingResponse cancelBooking(String bookingReference, RentalCancelRequest request);

    RentalBookingResponse markCompleted(String bookingReference);

    // -- Maintenance (called by RentalBookingExpiryScheduler) --

    void expireAbandonedPendingBookings();
}
