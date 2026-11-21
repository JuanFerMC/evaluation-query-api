package com.crediticio.evaluationquery.historial.infrastructure.output;

import com.crediticio.evaluationquery.historial.domain.EvaluacionHistorica;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;

/** Conversión de una fila del historial (columnas comunes de HU15 y US 5.1.2) en una EvaluacionHistorica. */
final class FilaEvaluacionHistorica {

    static final RowMapper<EvaluacionHistorica> MAPEADOR = (fila, numero) -> EvaluacionHistorica.reconstruir(
            fila.getLong("id_evaluacion"),
            fila.getObject("fecha_evaluacion", LocalDateTime.class),
            fila.getObject("score_total", Integer.class),
            fila.getInt("suma_detalles"),
            fila.getString("categoria"),
            fila.getObject("version_politica", Integer.class),
            fila.getString("estado"));

    private FilaEvaluacionHistorica() {
    }
}
