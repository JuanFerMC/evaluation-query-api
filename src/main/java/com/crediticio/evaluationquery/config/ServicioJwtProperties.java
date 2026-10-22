package com.crediticio.evaluationquery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Identidad de servicio S1 de la saga de HU14: valida localmente los tokens que firma scoring-api. Solo se
 * configuran claves públicas (PEM X.509 externos), cada una con su kid; la clave anterior es opcional y permite
 * rotar sin cortar la saga.
 */
@ConfigurationProperties("evaluationquery.servicio-jwt")
public record ServicioJwtProperties(
        String emisor,
        String sujeto,
        String audiencia,
        String permiso,
        String permisoProyeccion,
        String tipo,
        Duration tolerancia,
        Duration duracionMaxima,
        String idClave,
        Resource clavePublica,
        String idClaveAnterior,
        Resource clavePublicaAnterior) {
}
