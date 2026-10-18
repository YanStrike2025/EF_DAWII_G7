package com.cibertec.courierservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// STUB (Rol 3): el Rol 2 ya puede inyectar esta clase y llamar a publicar().
// El Rol 3 reemplaza el cuerpo por KafkaTemplate.send(topic, codigoTracking, event).
// Molde: products-services/kafka del repo del curso.
@Component
public class TrackingProducer {

	private static final Logger LOGGER = LoggerFactory.getLogger(TrackingProducer.class);

	public void publicar(TrackingStatusEvent event) {
		LOGGER.info("[STUB kafka] pendiente de implementar por Rol 3: {}", event);
	}
}
