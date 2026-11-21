package com.crediticio.evaluationquery.historial.domain;

import com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * US 5.1.2: filtros opcionales del historial de evaluaciones, que se combinan con AND. Las fechas son días
 * completos e inclusivos sobre evaluacion.fecha_evaluacion (TIMESTAMP sin zona, sin conversiones): desde el
 * inicio de fechaDesde hasta antes del inicio del día siguiente a fechaHasta. numeroDocumento es el documento del
 * solicitante (no su identificador interno): se compara exacto tras recortar espacios y, vacío, no filtra.
 */
public record FiltroHistorial(LocalDate fechaDesde, LocalDate fechaHasta, String numeroDocumento,
        CategoriaRiesgo categoria) {

    public FiltroHistorial {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new FiltroHistorialInvalidoException(
                    "El parámetro 'fechaDesde' no puede ser posterior a 'fechaHasta'");
        }
        numeroDocumento = numeroDocumento == null || numeroDocumento.isBlank() ? null : numeroDocumento.trim();
    }

    public static FiltroHistorial sinFiltros() {
        return new FiltroHistorial(null, null, null, null);
    }

    /** Límite inferior inclusivo: inicio del día fechaDesde; null si no se filtra. */
    public LocalDateTime inicioInclusivo() {
        return fechaDesde == null ? null : fechaDesde.atStartOfDay();
    }

    /** Límite superior exclusivo: inicio del día siguiente a fechaHasta (incluye todo ese día); null si no se filtra. */
    public LocalDateTime finExclusivo() {
        return fechaHasta == null ? null : fechaHasta.plusDays(1).atStartOfDay();
    }
}
