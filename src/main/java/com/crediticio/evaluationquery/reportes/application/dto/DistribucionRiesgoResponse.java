package com.crediticio.evaluationquery.reportes.application.dto;

import java.util.List;

/**
 * US 7.1.1: distribución de evaluaciones por nivel de riesgo. distribucion siempre trae BAJO, MEDIO y ALTO, en ese
 * orden (con cantidad 0 si no hay); totalEvaluaciones es exactamente la suma de sus cantidades.
 * evaluacionesSinClasificar (anteriores a HU12, sin categoría) se informa aparte y no entra en el total.
 */
public record DistribucionRiesgoResponse(
        long totalEvaluaciones,
        List<NivelRiesgoResponse> distribucion,
        long evaluacionesSinClasificar) {
}
