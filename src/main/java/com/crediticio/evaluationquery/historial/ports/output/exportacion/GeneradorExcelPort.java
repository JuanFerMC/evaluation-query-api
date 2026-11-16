package com.crediticio.evaluationquery.historial.ports.output.exportacion;

import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;

import java.util.List;

/** Puerto de salida para producir un libro Excel con historiales agrupados por solicitante. */
public interface GeneradorExcelPort {

    byte[] generar(List<HistorialSolicitanteExportacion> historiales);
}
