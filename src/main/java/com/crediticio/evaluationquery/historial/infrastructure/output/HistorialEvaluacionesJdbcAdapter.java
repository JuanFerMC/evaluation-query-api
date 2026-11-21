package com.crediticio.evaluationquery.historial.infrastructure.output;

import com.crediticio.evaluationquery.historial.domain.EvaluacionHistorica;
import com.crediticio.evaluationquery.historial.ports.output.HistorialEvaluacionesPort;
import com.crediticio.evaluationquery.historial.ports.output.SolicitanteExistenciaPort;
import com.crediticio.evaluationquery.historial.ports.output.SolicitanteNombrePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lee con SELECT las tablas de scoring-api. De solicitante solo consulta el identificador (CA13); la suma de
 * los detalles solo se usa como puntaje derivado de las evaluaciones anteriores a HU12 (P-HU15-01). Excluye
 * las evaluaciones PENDIENTE de la saga de HU14 (CA08); incluye las COMPLETADA y las anteriores a la saga.
 */
@Component
public class HistorialEvaluacionesJdbcAdapter implements SolicitanteExistenciaPort, SolicitanteNombrePort, HistorialEvaluacionesPort {

    private static final String EXISTE_SOLICITANTE =
            "SELECT COUNT(*) FROM solicitante WHERE id_solicitante = :idSolicitante";

    private static final String CONSULTA_NOMBRE =
            "SELECT nombre_completo FROM solicitante WHERE id_solicitante = :idSolicitante";

    private static final String CONSULTA_HISTORIAL = """
            SELECT e.id_evaluacion, e.fecha_evaluacion, e.score_total, e.categoria, e.estado,
                   p.version AS version_politica,
                   COALESCE(SUM(d.puntaje_obtenido), 0) AS suma_detalles
            FROM evaluacion e
            LEFT JOIN politica_clasificacion p ON p.id_politica = e.id_politica
            LEFT JOIN detalle_evaluacion d ON d.id_evaluacion = e.id_evaluacion
            WHERE e.id_solicitante = :idSolicitante
              AND (e.estado IS NULL OR e.estado <> 'PENDIENTE')
            GROUP BY e.id_evaluacion, e.fecha_evaluacion, e.score_total, e.categoria, e.estado, p.version
            ORDER BY e.fecha_evaluacion DESC, e.id_evaluacion DESC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public HistorialEvaluacionesJdbcAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existe(long idSolicitante) {
        Long cantidad = jdbcTemplate.queryForObject(EXISTE_SOLICITANTE, parametros(idSolicitante), Long.class);
        return cantidad != null && cantidad > 0;
    }

    @Override
    public String obtenerNombreCompleto(long idSolicitante) {
        return jdbcTemplate.queryForObject(CONSULTA_NOMBRE, parametros(idSolicitante), String.class);
    }

    @Override
    public List<EvaluacionHistorica> listarPorSolicitante(long idSolicitante) {
        return jdbcTemplate.query(CONSULTA_HISTORIAL, parametros(idSolicitante),
                (fila, numero) -> EvaluacionHistorica.reconstruir(
                        fila.getLong("id_evaluacion"),
                        fila.getObject("fecha_evaluacion", LocalDateTime.class),
                        fila.getObject("score_total", Integer.class),
                        fila.getInt("suma_detalles"),
                        fila.getString("categoria"),
                        fila.getObject("version_politica", Integer.class),
                        fila.getString("estado")));
    }

    private static MapSqlParameterSource parametros(long idSolicitante) {
        return new MapSqlParameterSource("idSolicitante", idSolicitante);
    }
}
