package com.crediticio.evaluationquery.historial.application.dto;

import java.util.List;

/**
 * US 5.1.2: página del historial filtrado. totalRegistros es el total de evaluaciones que cumplen los filtros en
 * todas las páginas; sin coincidencias, evaluaciones es una lista vacía y totalRegistros y totalPaginas son 0.
 * Cada elemento tiene el formato del historial de HU15, sin datos personales del solicitante (CA13).
 */
public record PaginaHistorialResponse(
        List<EvaluacionHistorialResponse> evaluaciones,
        int pagina,
        int tamano,
        long totalRegistros,
        int totalPaginas) {
}
