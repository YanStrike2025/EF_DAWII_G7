package com.cibertec.trackingservice.rabbitmq;

// Copia del contrato de courier-service (mismos campos y nombres).
public record EnvioNotificacionEvent(
		String codigoTracking,
		Long remitenteId,
		String estado,
		String mensaje
) {
}
