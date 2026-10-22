package com.crediticio.evaluationquery.recomendaciones.infrastructure.output;

import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionParaRecomendar;
import com.crediticio.evaluationquery.recomendaciones.domain.FactorEvaluado;
import com.crediticio.evaluationquery.recomendaciones.ports.output.EvaluacionParaRecomendarPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Lee con SELECT el estado de la saga, la clasificación persistida y los factores de una evaluación de
 * scoring-api para las consultas públicas y compatibilidad con el endpoint interno antiguo (HU14 CA02, CA09).
 * La nueva generación recibe el snapshot explícito; la contribución nunca se recalcula.
 */
@Component
public class EvaluacionParaRecomendarJdbcAdapter implements EvaluacionParaRecomendarPort {

    private static final String CONSULTA_EVALUACION = """
            SELECT e.estado, e.categoria
            FROM evaluacion e
            WHERE e.id_evaluacion = :idEvaluacion
            """;

    private static final String CONSULTA_FACTORES = """
            SELECT r.variable, d.condicion_cumplida, d.puntaje_obtenido
            FROM detalle_evaluacion d
            JOIN regla_scoring rs ON rs.id_regla = d.id_regla
            JOIN riesgo r ON r.id_riesgo = rs.id_riesgo
            WHERE d.id_evaluacion = :idEvaluacion
            ORDER BY d.id_detalle_evaluacion
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EvaluacionParaRecomendarJdbcAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<EvaluacionParaRecomendar> buscar(long idEvaluacion) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("idEvaluacion", idEvaluacion);
        List<DatosEvaluacion> evaluaciones = jdbcTemplate.query(CONSULTA_EVALUACION, parametros,
                (fila, numero) -> new DatosEvaluacion(fila.getString("estado"), fila.getString("categoria")));
        if (evaluaciones.isEmpty()) {
            return Optional.empty();
        }
        List<FactorEvaluado> factores = jdbcTemplate.query(CONSULTA_FACTORES, parametros,
                (fila, numero) -> new FactorEvaluado(
                        fila.getString("variable"),
                        fila.getBoolean("condicion_cumplida"),
                        fila.getInt("puntaje_obtenido")));
        DatosEvaluacion datos = evaluaciones.get(0);
        return Optional.of(new EvaluacionParaRecomendar(idEvaluacion, datos.estado(), datos.categoria(), factores));
    }

    private record DatosEvaluacion(String estado, String categoria) {
    }
}
