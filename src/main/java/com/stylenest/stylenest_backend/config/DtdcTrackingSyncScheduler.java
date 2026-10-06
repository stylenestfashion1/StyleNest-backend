package com.stylenest.stylenest_backend.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stylenest.stylenest_backend.entity.Shipment;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.repository.ShipmentRepository;
import com.stylenest.stylenest_backend.service.ShipmentService;

import lombok.RequiredArgsConstructor;

/**
 * Periodically polls in-flight DTDC consignments and synchronizes their
 * tracking status into StyleNest (e.g. IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED).
 * Automatically updates database records, shipment history, and dispatches
 * milestone email notifications to customers.
 */
@Component
@RequiredArgsConstructor
public class DtdcTrackingSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(DtdcTrackingSyncScheduler.class);

    private static final List<ShipmentStatus> TERMINAL_STATUSES = List.of(
            ShipmentStatus.DELIVERED,
            ShipmentStatus.CANCELLED,
            ShipmentStatus.RETURNED
    );

    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;

    // Runs every 30 minutes with an initial delay of 2 minutes
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void syncInFlightDtdcShipments() {
        try {
            List<Shipment> inFlight = shipmentRepository
                    .findByCourierNameIgnoreCaseAndTrackingNumberIsNotNullAndShipmentStatusNotIn("DTDC", TERMINAL_STATUSES);

            if (inFlight.isEmpty()) {
                return;
            }

            log.info("DTDC Tracking Sync: polling {} active in-flight shipment(s)...", inFlight.size());

            for (Shipment shipment : inFlight) {
                if (shipment.getOrder() == null) continue;
                try {
                    shipmentService.refreshDtdcTracking(shipment.getOrder().getId());
                } catch (Exception ex) {
                    log.warn("Failed background DTDC sync for shipment {} (AWB {}): {}",
                            shipment.getId(), shipment.getTrackingNumber(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Error in DTDC background tracking sync scheduler: {}", ex.getMessage());
        }
    }
}
