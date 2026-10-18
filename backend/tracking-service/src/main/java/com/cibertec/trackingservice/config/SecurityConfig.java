package com.cibertec.trackingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// PROVISIONAL: deja todo abierto para que el Rol 3 pueda probar el historial desde el primer dia.
// El Rol 4 lo reemplaza por la validacion del JWT (copiar JwtService/JwtAuthenticationFilter de courier-service,
// solo para validar; este servicio no emite tokens) y exige autenticacion en /notificaciones y /tracking.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.build();
	}
}
