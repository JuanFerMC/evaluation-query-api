package com.crediticio.evaluationquery.proyeccion.infrastructure.output;

import com.crediticio.evaluationquery.proyeccion.ports.output.ProyeccionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProyeccionJdbcAdapter implements ProyeccionRepositoryPort {

    private static final String REGISTRAR_SOLICITANTE = """
            INSERT INTO proyeccion_solicitante (id_solicitante)
            VALUES (?)
            ON CONFLICT (id_solicitante) DO NOTHING
            """;
    private static final String COMPLETAR_EVALUACION = """
            UPDATE proyeccion_evaluacion
               SET estado = 'COMPLETADA'
             WHERE id_evaluacion = ? AND estado = 'PENDIENTE'
            """;
    private static final String CONSULTAR_ESTADO = """
            SELECT estado FROM proyeccion_evaluacion WHERE id_evaluacion = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProyeccionJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void registrarSolicitante(long idSolicitante) {
        jdbcTemplate.update(REGISTRAR_SOLICITANTE, idSolicitante);
    }

    @Override
    public ResultadoConfirmacion completarEvaluacion(long idEvaluacion) {
        if (jdbcTemplate.update(COMPLETAR_EVALUACION, idEvaluacion) == 1) {
            return ResultadoConfirmacion.ACTUALIZADA;
        }
        List<String> estados = jdbcTemplate.query(CONSULTAR_ESTADO,
                (resultado, fila) -> resultado.getString("estado"), idEvaluacion);
        if (estados.isEmpty()) {
            return ResultadoConfirmacion.INEXISTENTE;
        }
        return "COMPLETADA".equals(estados.get(0))
                ? ResultadoConfirmacion.YA_COMPLETADA : ResultadoConfirmacion.ESTADO_INCOMPATIBLE;
    }
}
