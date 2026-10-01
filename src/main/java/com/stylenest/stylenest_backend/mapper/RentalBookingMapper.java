package com.stylenest.stylenest_backend.mapper;

import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.dto.rental.RentalBookingResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalBookingSummaryResponse;
import com.stylenest.stylenest_backend.entity.RentalBooking;

@Component
public class RentalBookingMapper {

    public RentalBookingResponse toResponse(RentalBooking booking) {

        return RentalBookingResponse.builder()
                .bookingReference(booking.getBookingReference())
                .itemId(booking.getItem().getId())
                .itemName(booking.getItem().getName())
                .itemColour(booking.getItem().getColour())
                .itemImageUrl(booking.getItem().getImages().isEmpty() ? null : booking.getItem().getImages().get(0).getImageUrl())
                .startDate(booking.getStartDate())
                .endDate(booking.getEndDate())
                .rentalDays(booking.getRentalDays())
                .dailyRate(booking.getDailyRate())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .customerName(booking.getCustomerName())
                .customerPhone(booking.getCustomerPhone())
                .transactionId(booking.getTransactionId())
                .paymentScreenshotUrl(booking.getPaymentScreenshotUrl())
                .createdAt(booking.getCreatedAt())
                .build();
    }

    public RentalBookingSummaryResponse toSummaryResponse(RentalBooking booking) {

        return RentalBookingSummaryResponse.builder()
                .bookingReference(booking.getBookingReference())
                .itemName(booking.getItem().getName())
                .customerName(booking.getCustomerName())
                .customerPhone(booking.getCustomerPhone())
                .startDate(booking.getStartDate())
                .endDate(booking.getEndDate())
                .rentalDays(booking.getRentalDays())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .build();
    }
}
