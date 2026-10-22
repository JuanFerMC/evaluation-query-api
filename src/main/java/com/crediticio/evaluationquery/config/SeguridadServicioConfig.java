package com.crediticio.evaluationquery.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de seguridad de /internal/**, evaluada antes que la de usuarios. Solo acepta el token de servicio
 * S1 (ServicioJwtConfig) con el permiso específico de cada operación; un token de usuario se rechaza con 401
 * porque su firma,
 * emisor y audiencia no corresponden. Solo existe con evaluationquery.saga.habilitada=true; si no, /internal/**
 * lo deniega la cadena de usuarios.
 */
@Configuration
@ConditionalOnProperty(prefix = "evaluationquery.saga", name = "habilitada", havingValue = "true")
@EnableConfigurationProperties(ServicioJwtProperties.class)
public class SeguridadServicioConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain cadenaServicio(HttpSecurity http, ServicioJwtProperties propiedades) throws Exception {
        RespuestaSeguridadJson respuestas = new RespuestaSeguridadJson();
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(autorizacion -> autorizacion
                        .requestMatchers(HttpMethod.PUT, "/internal/v1/evaluaciones/*/recomendaciones")
                                .hasAuthority("SCOPE_" + propiedades.permiso())
                        .requestMatchers(HttpMethod.PUT, "/internal/v1/proyeccion/solicitantes/*")
                                .hasAuthority("SCOPE_" + propiedades.permisoProyeccion())
                        .requestMatchers(HttpMethod.PUT, "/internal/v1/proyeccion/evaluaciones/*/completada")
                                .hasAuthority("SCOPE_" + propiedades.permisoProyeccion())
                        .anyRequest().denyAll())
                .oauth2ResourceServer(recursos -> recursos
                        .jwt(jwt -> jwt.decoder(ServicioJwtConfig.decodificador(propiedades)))
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas));
        return http.build();
    }
}
