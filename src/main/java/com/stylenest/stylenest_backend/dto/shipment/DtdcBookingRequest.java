package com.stylenest.stylenest_backend.dto.shipment;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * The only fields an admin actually supplies when booking a DTDC shipment
 * -- the package's physical weight/dimensions, known only once it's
 * packed. Everything else DTDC's Order Upload API needs (customer,
 * address, COD/declared value, invoice reference) comes from the
 * authoritative Order itself, never from admin-typed input (see
 * ShipmentServiceImpl.bookDtdcShipment).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DtdcBookingRequest {

    @NotNull(message = "weightKg is required")
    @DecimalMin(value = "0.01", message = "weightKg must be greater than 0")
    private BigDecimal weightKg;

    @NotNull(message = "lengthCm is required")
    @DecimalMin(value = "0.1", message = "lengthCm must be greater than 0")
    private BigDecimal lengthCm;

    @NotNull(message = "widthCm is required")
    @DecimalMin(value = "0.1", message = "widthCm must be greater than 0")
    private BigDecimal widthCm;

    @NotNull(message = "heightCm is required")
    @DecimalMin(value = "0.1", message = "heightCm must be greater than 0")
    private BigDecimal heightCm;

    @Min(value = 1, message = "numPieces must be at least 1")
    @Builder.Default
    private Integer numPieces = 1;
}
