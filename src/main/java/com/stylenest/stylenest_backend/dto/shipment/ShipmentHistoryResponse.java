package com.stylenest.stylenest_backend.dto.shipment;

import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.ShipmentStatus;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentHistoryResponse {

    private ShipmentStatus status;

    private LocalDateTime timestamp;

    private String description;

    private String location;
}
