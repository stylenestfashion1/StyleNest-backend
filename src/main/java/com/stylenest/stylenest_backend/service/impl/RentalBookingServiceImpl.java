package com.stylenest.stylenest_backend.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.stylenest.stylenest_backend.dto.rental.RentalBookingCreateRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalBookingResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalBookingSummaryResponse;
import com.stylenest.stylenest_backend.dto.rental.RentalCancelRequest;
import com.stylenest.stylenest_backend.dto.rental.RentalUnavailableDatesResponse;
import com.stylenest.stylenest_backend.entity.RentalBooking;
import com.stylenest.stylenest_backend.entity.RentalBookingSegment;
import com.stylenest.stylenest_backend.entity.RentalCatalogItem;
import com.stylenest.stylenest_backend.entity.RentalSettings;
import com.stylenest.stylenest_backend.enums.RentalBookingStatus;
import com.stylenest.stylenest_backend.enums.RentalCatalogStatus;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.exception.DuplicateResourceException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.RentalBookingMapper;
import com.stylenest.stylenest_backend.repository.RentalBookingRepository;
import com.stylenest.stylenest_backend.repository.RentalBookingSegmentRepository;
import com.stylenest.stylenest_backend.repository.RentalCatalogItemRepository;
import com.stylenest.stylenest_backend.service.RentalBookingService;
import com.stylenest.stylenest_backend.service.RentalImageStorageService;
import com.stylenest.stylenest_backend.service.RentalSettingsService;

import lombok.RequiredArgsConstructor;

/**
 * Fully isolated from every retail/order/payment service -- no dependency
 * on OrderRepository/PaymentService/Cashfree anywhere in this class (see
 * spec section 24). A booking's real availability is always derived from
 * {@link RentalBookingSegment} rows, never from RentalBooking's own
 * startDate/endDate directly -- see that entity's javadoc.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RentalBookingServiceImpl implements RentalBookingService {

    private final RentalBookingRepository rentalBookingRepository;
    private final RentalBookingSegmentRepository rentalBookingSegmentRepository;
    private final RentalCatalogItemRepository rentalCatalogItemRepository;
    private final RentalBookingMapper rentalBookingMapper;
    private final RentalSettingsService rentalSettingsService;
    private final RentalImageStorageService rentalImageStorageService;

    @Override
    @Transactional(readOnly = true)
    public RentalUnavailableDatesResponse getUnavailableDates(String shareToken, Long itemId) {

        RentalCatalogItem item = findPublicItem(shareToken, itemId);
        RentalSettings settings = rentalSettingsService.currentSettings();

        LocalDateTime pendingCutoff = LocalDateTime.now().minusMinutes(settings.getPendingPaymentExpiryMinutes());

        List<RentalBookingSegment> blocking =
                rentalBookingSegmentRepository.findAllBlockingSegmentsForItem(item.getId(), pendingCutoff);

        List<LocalDate> unavailable = new ArrayList<>();
        for (RentalBookingSegment segment : blocking) {
            LocalDate day = segment.getStartDate().isBefore(settings.getSeasonStartDate())
                    ? settings.getSeasonStartDate() : segment.getStartDate();
            LocalDate last = segment.getEndDate().isAfter(settings.getSeasonEndDate())
                    ? settings.getSeasonEndDate() : segment.getEndDate();
            while (!day.isAfter(last)) {
                unavailable.add(day);
                day = day.plusDays(1);
            }
        }

        return RentalUnavailableDatesResponse.builder()
                .seasonStartDate(settings.getSeasonStartDate())
                .seasonEndDate(settings.getSeasonEndDate())
                .unavailableDates(unavailable.stream().distinct().sorted().toList())
                .build();
    }

    @Override
    public RentalBookingResponse createBooking(String shareToken, Long itemId, RentalBookingCreateRequest request) {

        RentalCatalogItem item = findPublicItem(shareToken, itemId);
        RentalSettings settings = rentalSettingsService.currentSettings();

        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();

        if (start.isAfter(end)) {
            throw new BadRequestException("The pickup date must be on or before the return date.");
        }

        if (start.isBefore(settings.getSeasonStartDate()) || end.isAfter(settings.getSeasonEndDate())) {
            throw new BadRequestException(
                    "Selected dates must fall within the rental season ("
                            + settings.getSeasonStartDate() + " to " + settings.getSeasonEndDate() + ").");
        }

        // Re-check availability server-side, inside this same transaction,
        // no matter what the frontend calendar already disabled -- see
        // spec section 6/21 ("never trust the frontend").
        assertAvailable(item.getId(), start, end, settings);

        int rentalDays = (int) ChronoUnit.DAYS.between(start, end) + 1;

        RentalBooking booking = RentalBooking.builder()
                .item(item)
                .startDate(start)
                .endDate(end)
                .rentalDays(rentalDays)
                .dailyRate(item.getRentalPrice())
                .totalAmount(item.getRentalPrice().multiply(java.math.BigDecimal.valueOf(rentalDays)))
                .status(RentalBookingStatus.PENDING_PAYMENT)
                .customerName(request.getCustomerName().trim())
                .customerPhone(request.getCustomerPhone().trim())
                .termsAcceptedAt(LocalDateTime.now())
                .build();

        booking = persistWithBookingReference(booking);

        RentalBookingSegment segment = RentalBookingSegment.builder()
                .booking(booking)
                .startDate(start)
                .endDate(end)
                .active(true)
                .build();

        rentalBookingSegmentRepository.save(segment);

        return rentalBookingMapper.toResponse(booking);
    }

    @Override
    public RentalBookingResponse submitPayment(
            String shareToken, String bookingReference, String transactionId, MultipartFile screenshot) {

        RentalBooking booking = findBookingScopedToShareToken(shareToken, bookingReference);

        if (booking.getStatus() != RentalBookingStatus.PENDING_PAYMENT) {
            throw new BadRequestException("This booking can no longer accept payment details.");
        }

        booking.setTransactionId(transactionId.trim());

        if (screenshot != null && !screenshot.isEmpty()) {
            String previousUrl = booking.getPaymentScreenshotUrl();
            booking.setPaymentScreenshotUrl(rentalImageStorageService.store(screenshot).getUrl());
            if (previousUrl != null) {
                rentalImageStorageService.deleteIfManaged(previousUrl);
            }
        }

        booking.setStatus(RentalBookingStatus.PAYMENT_SUBMITTED);

        return rentalBookingMapper.toResponse(rentalBookingRepository.save(booking));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RentalBookingSummaryResponse> getAllBookings() {

        return rentalBookingRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(rentalBookingMapper::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RentalBookingResponse getBookingByReference(String bookingReference) {

        return rentalBookingMapper.toResponse(findBooking(bookingReference));
    }

    @Override
    public RentalBookingResponse confirmBooking(String bookingReference) {

        RentalBooking booking = findBooking(bookingReference);

        if (booking.getStatus() == RentalBookingStatus.CANCELLED
                || booking.getStatus() == RentalBookingStatus.COMPLETED) {
            throw new BadRequestException("A " + booking.getStatus() + " booking cannot be confirmed.");
        }

        booking.setStatus(RentalBookingStatus.CONFIRMED);

        return rentalBookingMapper.toResponse(rentalBookingRepository.save(booking));
    }

    @Override
    public RentalBookingResponse cancelBooking(String bookingReference, RentalCancelRequest request) {

        RentalBooking booking = findBooking(bookingReference);

        if (booking.getStatus() == RentalBookingStatus.CANCELLED) {
            throw new BadRequestException("This booking is already cancelled.");
        }

        boolean fullCancellation = request == null || request.getStartDate() == null || request.getEndDate() == null;

        List<RentalBookingSegment> segments = rentalBookingSegmentRepository.findAllByBookingId(booking.getId());

        if (fullCancellation) {

            LocalDateTime now = LocalDateTime.now();

            segments.stream()
                    .filter(RentalBookingSegment::isActive)
                    .forEach(s -> {
                        s.setActive(false);
                        s.setCancelledAt(now);
                    });

            rentalBookingSegmentRepository.saveAll(segments);

            booking.setStatus(RentalBookingStatus.CANCELLED);

            return rentalBookingMapper.toResponse(rentalBookingRepository.save(booking));
        }

        // Partial cancellation -- see RentalBookingSegment's javadoc. The
        // requested range must fall entirely inside exactly one currently
        // active segment; we never split across two segments in one call.
        LocalDate cancelStart = request.getStartDate();
        LocalDate cancelEnd = request.getEndDate();

        if (cancelStart.isAfter(cancelEnd)) {
            throw new BadRequestException("The cancellation start date must be on or before the end date.");
        }

        RentalBookingSegment target = segments.stream()
                .filter(RentalBookingSegment::isActive)
                .filter(s -> !cancelStart.isBefore(s.getStartDate()) && !cancelEnd.isAfter(s.getEndDate()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "The dates to cancel are not currently part of an active segment of this booking."));

        LocalDateTime now = LocalDateTime.now();
        target.setActive(false);
        target.setCancelledAt(now);
        rentalBookingSegmentRepository.save(target);

        if (target.getStartDate().isBefore(cancelStart)) {
            rentalBookingSegmentRepository.save(RentalBookingSegment.builder()
                    .booking(booking)
                    .startDate(target.getStartDate())
                    .endDate(cancelStart.minusDays(1))
                    .active(true)
                    .build());
        }

        if (target.getEndDate().isAfter(cancelEnd)) {
            rentalBookingSegmentRepository.save(RentalBookingSegment.builder()
                    .booking(booking)
                    .startDate(cancelEnd.plusDays(1))
                    .endDate(target.getEndDate())
                    .active(true)
                    .build());
        }

        boolean anyActiveLeft = rentalBookingSegmentRepository.findAllByBookingId(booking.getId())
                .stream()
                .anyMatch(RentalBookingSegment::isActive);

        if (!anyActiveLeft) {
            booking.setStatus(RentalBookingStatus.CANCELLED);
            rentalBookingRepository.save(booking);
        }

        return rentalBookingMapper.toResponse(booking);
    }

    @Override
    public RentalBookingResponse markCompleted(String bookingReference) {

        RentalBooking booking = findBooking(bookingReference);

        if (booking.getStatus() != RentalBookingStatus.CONFIRMED) {
            throw new BadRequestException("Only a confirmed booking can be marked completed.");
        }

        booking.setStatus(RentalBookingStatus.COMPLETED);

        return rentalBookingMapper.toResponse(rentalBookingRepository.save(booking));
    }

    @Override
    public void expireAbandonedPendingBookings() {

        RentalSettings settings = rentalSettingsService.currentSettings();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(settings.getPendingPaymentExpiryMinutes());

        List<RentalBooking> abandoned =
                rentalBookingRepository.findAllByStatusAndCreatedAtBefore(RentalBookingStatus.PENDING_PAYMENT, cutoff);

        for (RentalBooking booking : abandoned) {

            booking.setStatus(RentalBookingStatus.CANCELLED);
            rentalBookingRepository.save(booking);

            LocalDateTime now = LocalDateTime.now();
            List<RentalBookingSegment> segments = rentalBookingSegmentRepository.findAllByBookingId(booking.getId());
            segments.stream()
                    .filter(RentalBookingSegment::isActive)
                    .forEach(s -> {
                        s.setActive(false);
                        s.setCancelledAt(now);
                    });
            rentalBookingSegmentRepository.saveAll(segments);
        }
    }

    private void assertAvailable(Long itemId, LocalDate start, LocalDate end, RentalSettings settings) {

        LocalDateTime pendingCutoff = LocalDateTime.now().minusMinutes(settings.getPendingPaymentExpiryMinutes());

        List<RentalBookingSegment> blocking =
                rentalBookingSegmentRepository.findBlockingSegments(itemId, start, end, pendingCutoff);

        if (!blocking.isEmpty()) {
            throw new DuplicateResourceException(
                    "Sorry, this lehenga is already booked for some of the selected dates. Please choose different dates.");
        }
    }

    /** Looks up an item via a share token, validating the catalog is ACTIVE and actually contains it -- same trust boundary as RentalCatalogServiceImpl.getPublicCatalogByShareToken. */
    private RentalCatalogItem findPublicItem(String shareToken, Long itemId) {

        RentalCatalogItem item = rentalCatalogItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental item not found."));

        if (item.getCatalog() == null
                || !item.getCatalog().getShareToken().equals(shareToken)
                || item.getCatalog().getStatus() != RentalCatalogStatus.ACTIVE) {
            throw new ResourceNotFoundException("Rental item not found.");
        }

        return item;
    }

    private RentalBooking findBookingScopedToShareToken(String shareToken, String bookingReference) {

        RentalBooking booking = rentalBookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));

        if (!booking.getItem().getCatalog().getShareToken().equals(shareToken)) {
            throw new ResourceNotFoundException("Booking not found.");
        }

        return booking;
    }

    private RentalBooking findBooking(String bookingReference) {

        return rentalBookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingReference));
    }

    /** Mirrors InvoiceGenerationServiceImpl.persistWithInvoiceNumber -- save once for the id, then stamp the human-readable reference, save again. */
    private RentalBooking persistWithBookingReference(RentalBooking booking) {

        booking = rentalBookingRepository.save(booking);

        String year = String.valueOf(booking.getStartDate().getYear());
        booking.setBookingReference("RENT-" + year + "-" + String.format("%6s", booking.getId()).replace(' ', '0'));

        return rentalBookingRepository.save(booking);
    }
}
