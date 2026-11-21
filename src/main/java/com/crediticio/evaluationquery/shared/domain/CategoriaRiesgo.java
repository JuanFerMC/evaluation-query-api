package com.crediticio.evaluationquery.shared.domain;

/**
 * Nivel de riesgo tal como lo persiste scoring-api en evaluacion.categoria (rangos de la política de
 * clasificación: BAJO, MEDIO, ALTO). Son los valores almacenados, sin equivalencias con otras etiquetas. Lo usan
 * el historial filtrado (US 5.1.2) y el reporte de distribución (US 7.1.1).
 */
public enum CategoriaRiesgo {
    BAJO,
    MEDIO,
    ALTO
}
