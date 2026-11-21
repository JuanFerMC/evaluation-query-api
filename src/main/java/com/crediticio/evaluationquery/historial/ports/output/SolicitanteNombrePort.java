package com.crediticio.evaluationquery.historial.ports.output;

/** Puerto de lectura mínima del nombre para identificar al solicitante en los reportes. */
public interface SolicitanteNombrePort {

    String obtenerNombreCompleto(long idSolicitante);
}
