package com.stylenest.stylenest_backend.service.courier;

import java.time.LocalDateTime;
import java.util.Optional;

import com.stylenest.stylenest_backend.enums.ShipmentStatus;

/**
 * Abstraction for a future real courier integration. The client will
 * provide courier API credentials later -- when that happens, implement
 * this interface for the chosen courier and feed its result into
 * ShipmentService.updateShipment(...) (or a new scheduled/webhook-driven
 * caller); no other part of the shipment system needs to change.
 *
 * Intentionally NOT implemented or registered as a Spring bean yet -- no
 * courier provider is hardcoded or faked, per the current requirements.
 */
public interface CourierTrackingService {

    Optional<CourierTrackingSnapshot> fetchTracking(String courierName, String trackingNumber);

    record CourierTrackingSnapshot(
            ShipmentStatus status,
            String location,
            LocalDateTime eventTimestamp,
            String description
    ) {}
}
