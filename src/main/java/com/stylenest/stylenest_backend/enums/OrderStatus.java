package com.stylenest.stylenest_backend.enums;


public enum OrderStatus {
	PENDING,
	CONFIRMED,
	PROCESSING,
	COMPLETED,
	CANCELLED,

	// Deprecated: shipment progression now lives in ShipmentStatus, kept
	// only so any already-persisted rows with these values still
	// deserialize. No code path sets these anymore.
	@Deprecated PACKED,
	@Deprecated SHIPPED,
	@Deprecated DELIVERED
}
