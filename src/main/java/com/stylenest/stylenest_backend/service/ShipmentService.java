package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.shipment.ShipmentResponse;
import com.stylenest.stylenest_backend.dto.shipment.ShipmentUpdateRequest;

public interface ShipmentService {

    /** Admin-only: update a shipment's status/tracking/courier/ETA for the given order. */
    ShipmentResponse updateShipment(Long orderId, ShipmentUpdateRequest request);

    ShipmentResponse getShipmentByOrderId(Long orderId);
}
