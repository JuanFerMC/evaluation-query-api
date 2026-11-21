package com.crediticio.evaluationquery.historial.application.exportacion.dto;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;

import java.util.List;

/**
 * Historial completo de un solicitante dentro de una exportación.
 * Se conserva el identificador para distinguir los registros en exportaciones múltiples.
 */
public record HistorialSolicitanteExportacion(
        long idSolicitante,
        String nombreSolicitante,
        List<EvaluacionHistorialResponse> evaluaciones) {

    public HistorialSolicitanteExportacion {
        evaluaciones = List.copyOf(evaluaciones);
    }
}
