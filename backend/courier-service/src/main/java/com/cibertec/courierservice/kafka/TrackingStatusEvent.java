package com.cibertec.courierservice.kafka;

import java.time.Instant;

// Contrato del evento Kafka (no cambiar los campos sin avisar: tracking-service tiene una copia identica).
public record TrackingStatusEvent(
		String codigoTracking,
		String estado,
		Long remitenteId,
		String usuario,
		Instant timestamp
) {
}
