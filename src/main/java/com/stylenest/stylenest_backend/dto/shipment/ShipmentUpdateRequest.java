package com.stylenest.stylenest_backend.dto.shipment;

import java.time.LocalDate;

import com.stylenest.stylenest_backend.enums.ShipmentStatus;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentUpdateRequest {

    @NotNull(message = "shipmentStatus is required")
    private ShipmentStatus shipmentStatus;

    private String trackingNumber;

    private String courierName;

    private LocalDate estimatedDeliveryDate;

    // Optional free-text note/location recorded on the resulting
    // ShipmentHistory entry for this status change.
    private String description;

    private String location;
}
