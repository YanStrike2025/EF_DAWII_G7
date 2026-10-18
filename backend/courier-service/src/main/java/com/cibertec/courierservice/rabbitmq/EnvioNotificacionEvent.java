package com.cibertec.courierservice.rabbitmq;

// Contrato del mensaje RabbitMQ (tracking-service tiene una copia identica).
public record EnvioNotificacionEvent(
		String codigoTracking,
		Long remitenteId,
		String estado,
		String mensaje
) {
}
