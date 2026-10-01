package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.shipment.DtdcBookingRequest;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentResponse;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentUpdateRequest;

public interface ShipmentService {

    /** Admin-only: update a shipment's status/tracking/courier/ETA for the given order. */
    ShipmentResponse updateShipment(Long orderId, ShipmentUpdateRequest request);

    ShipmentResponse getShipmentByOrderId(Long orderId);

    /** Admin-only: books the order's shipment with DTDC once packed and weighed. */
    ShipmentResponse bookDtdcShipment(Long orderId, DtdcBookingRequest request);

    /** Admin-only: cancels a shipment previously booked with DTDC. */
    ShipmentResponse cancelDtdcShipment(Long orderId);

    /** Admin-only: fetches the printable DTDC shipping label (PDF bytes). */
    byte[] fetchDtdcLabel(Long orderId);

    /** Admin-only: pulls the latest tracking status from DTDC and records it. */
    ShipmentResponse refreshDtdcTracking(Long orderId);
}
