package com.stylenest.stylenest_backend.service.courier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import com.stylenest.stylenest_backend.enums.ShipmentStatus;

/**
 * Abstraction for a real courier integration, sitting between
 * ShipmentServiceImpl (which only knows StyleNest's own Order/Shipment
 * model) and a specific courier's API. DtdcShippingProviderClient is the
 * first and, for now, only implementation -- bookShipment/cancelShipment/
 * fetchLabel are always invoked by an explicit "via DTDC" admin action, so
 * they carry no courier-name parameter; fetchTracking keeps its original
 * courier-name parameter (a Shipment's courierName is free text an admin
 * can set to anything, not just "DTDC") and returns Optional.empty() for a
 * courier this implementation doesn't recognize.
 */
public interface CourierTrackingService {

    Optional<CourierTrackingSnapshot> fetchTracking(String courierName, String trackingNumber);

    /** Books a new consignment with the courier. Throws DtdcApiException on any failure. */
    BookingResult bookShipment(ShipmentBookingRequest request);

    /** Cancels a previously booked consignment. Throws DtdcApiException on any failure. */
    void cancelShipment(String trackingNumber);

    /** Fetches a printable shipping label (PDF bytes) for a booked consignment. */
    byte[] fetchLabel(String trackingNumber);

    record CourierTrackingSnapshot(
            ShipmentStatus status,
            String location,
            LocalDateTime eventTimestamp,
            String description
    ) {}

    /**
     * Everything needed to book a consignment, expressed in
     * courier-neutral terms -- no DTDC-specific field names. The caller
     * (ShipmentServiceImpl) builds this from the authoritative Order
     * (customer/address/COD/declared value) plus the package
     * weight/dimensions an admin supplies at pack time, which exist
     * nowhere else in the system.
     */
    record ShipmentBookingRequest(
            String customerReferenceNumber,
            String consigneeName,
            String consigneePhone,
            String consigneeAddressLine1,
            String consigneeAddressLine2,
            String consigneeCity,
            String consigneeState,
            String consigneePincode,
            boolean cashOnDelivery,
            BigDecimal codAmount,
            BigDecimal declaredValue,
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            int numPieces
    ) {}

    record BookingResult(String providerReferenceNumber) {}
}
