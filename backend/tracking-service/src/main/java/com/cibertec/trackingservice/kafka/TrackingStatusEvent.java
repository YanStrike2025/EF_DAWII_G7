package com.cibertec.trackingservice.kafka;

import java.time.Instant;

// Copia del contrato de courier-service (mismos campos y nombres).
public record TrackingStatusEvent(
		String codigoTracking,
		String estado,
		Long remitenteId,
		String usuario,
		Instant timestamp
) {
}
