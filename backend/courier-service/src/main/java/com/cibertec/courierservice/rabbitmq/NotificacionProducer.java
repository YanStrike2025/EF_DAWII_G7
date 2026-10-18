package com.cibertec.courierservice.rabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// STUB (Rol 4): el Rol 2 ya puede inyectar esta clase y llamar a publicar().
// El Rol 4 reemplaza el cuerpo por RabbitTemplate.convertAndSend(...) y agrega RabbitMQConfig.
// Molde: DAWII-T1/ms-recargas/rabbitmq del repo del curso.
@Component
public class NotificacionProducer {

	private static final Logger LOGGER = LoggerFactory.getLogger(NotificacionProducer.class);

	public void publicar(EnvioNotificacionEvent event) {
		LOGGER.info("[STUB rabbitmq] pendiente de implementar por Rol 4: {}", event);
	}
}
