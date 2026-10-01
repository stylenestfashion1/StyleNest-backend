package com.stylenest.stylenest_backend.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stylenest.stylenest_backend.entity.RentalBookingSegment;

public interface RentalBookingSegmentRepository extends JpaRepository<RentalBookingSegment, Long> {

    // The single query that decides real availability -- see
    // RentalBookingSegment's javadoc. A segment counts as "blocking" only
    // if it's active AND its parent booking is CONFIRMED/PAYMENT_SUBMITTED,
    // or a PENDING_PAYMENT hold created after `pendingCutoff` (i.e. not yet
    // expired). Inclusive-range overlap: segment.startDate <= :end AND
    // segment.endDate >= :start.
    @Query("""
            SELECT s FROM RentalBookingSegment s
            JOIN s.booking b
            WHERE b.item.id = :itemId
              AND s.active = true
              AND s.startDate <= :end
              AND s.endDate >= :start
              AND (
                    b.status IN (com.stylenest.stylenest_backend.enums.RentalBookingStatus.CONFIRMED,
                                 com.stylenest.stylenest_backend.enums.RentalBookingStatus.PAYMENT_SUBMITTED)
                    OR (b.status = com.stylenest.stylenest_backend.enums.RentalBookingStatus.PENDING_PAYMENT
                        AND b.createdAt >= :pendingCutoff)
                  )
            """)
    List<RentalBookingSegment> findBlockingSegments(
            @Param("itemId") Long itemId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("pendingCutoff") LocalDateTime pendingCutoff);

    // Every currently-blocking segment for an item, used to build the
    // customer-facing "these dates are unavailable" list for the whole
    // rental season in one query instead of one per day.
    @Query("""
            SELECT s FROM RentalBookingSegment s
            JOIN s.booking b
            WHERE b.item.id = :itemId
              AND s.active = true
              AND (
                    b.status IN (com.stylenest.stylenest_backend.enums.RentalBookingStatus.CONFIRMED,
                                 com.stylenest.stylenest_backend.enums.RentalBookingStatus.PAYMENT_SUBMITTED)
                    OR (b.status = com.stylenest.stylenest_backend.enums.RentalBookingStatus.PENDING_PAYMENT
                        AND b.createdAt >= :pendingCutoff)
                  )
            """)
    List<RentalBookingSegment> findAllBlockingSegmentsForItem(
            @Param("itemId") Long itemId,
            @Param("pendingCutoff") LocalDateTime pendingCutoff);

    List<RentalBookingSegment> findAllByBookingId(Long bookingId);
}
