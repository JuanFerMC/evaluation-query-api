package com.crediticio.evaluationquery.historial.ports.output.exportacion;

import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;

import java.util.List;

/** Puerto de salida para producir un documento PDF con historiales agrupados por solicitante. */
public interface GeneradorPdfPort {

    byte[] generar(List<HistorialSolicitanteExportacion> historiales);
}
