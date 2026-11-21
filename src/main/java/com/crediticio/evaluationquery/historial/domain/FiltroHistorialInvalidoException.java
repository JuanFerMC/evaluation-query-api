package com.crediticio.evaluationquery.historial.domain;

/** US 5.1.2: filtros o paginación del historial no válidos (respuesta 400 VALIDATION_ERROR). */
public class FiltroHistorialInvalidoException extends RuntimeException {

    public FiltroHistorialInvalidoException(String mensaje) {
        super(mensaje);
    }
}
