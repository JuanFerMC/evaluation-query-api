package com.crediticio.evaluationquery.reportes.application.dto;

import java.math.BigDecimal;

/** US 7.1.1: una categoría del reporte; porcentaje sobre totalEvaluaciones, con dos decimales. */
public record NivelRiesgoResponse(String categoria, long cantidad, BigDecimal porcentaje) {
}
