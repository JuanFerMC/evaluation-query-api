package com.crediticio.evaluationquery.historial.ports.input;

import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;

import java.util.List;

/**
 * Recupera los historiales completos que se incluirán en una exportación.
 */
public interface ConsultarHistorialExportacionUseCase {

    List<HistorialSolicitanteExportacion> consultar(List<Long> solicitanteIds);
}
