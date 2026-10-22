package com.crediticio.evaluationquery.proyeccion.infrastructure.output;

import com.crediticio.evaluationquery.proyeccion.ports.output.ProyeccionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ProyeccionJdbcAdapter implements ProyeccionRepositoryPort {

    private static final Pattern IDENTIFICADOR = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private final JdbcTemplate jdbcTemplate;
    private final String registrarSolicitante;
    private final String completarEvaluacion;
    private final String consultarEstado;

    public ProyeccionJdbcAdapter(JdbcTemplate jdbcTemplate,
            @Value("${evaluationquery.consulta.esquema:consulta}") String esquema) {
        if (esquema == null || !IDENTIFICADOR.matcher(esquema).matches()) {
            throw new IllegalStateException("evaluationquery.consulta.esquema debe ser un identificador en minúsculas");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.registrarSolicitante = """
                INSERT INTO %s.proyeccion_solicitante (id_solicitante) VALUES (?)
                ON CONFLICT (id_solicitante) DO NOTHING
                """.formatted(esquema);
        this.completarEvaluacion = """
                UPDATE %s.proyeccion_evaluacion SET estado = 'COMPLETADA'
                WHERE id_evaluacion = ? AND estado = 'PENDIENTE'
                """.formatted(esquema);
        this.consultarEstado = "SELECT estado FROM %s.proyeccion_evaluacion WHERE id_evaluacion = ?"
                .formatted(esquema);
    }

    @Override
    public void registrarSolicitante(long idSolicitante) {
        jdbcTemplate.update(registrarSolicitante, idSolicitante);
    }

    @Override
    public ResultadoConfirmacion completarEvaluacion(long idEvaluacion) {
        if (jdbcTemplate.update(completarEvaluacion, idEvaluacion) == 1) {
            return ResultadoConfirmacion.ACTUALIZADA;
        }
        List<String> estados = jdbcTemplate.query(consultarEstado,
                (resultado, fila) -> resultado.getString("estado"), idEvaluacion);
        if (estados.isEmpty()) {
            return ResultadoConfirmacion.INEXISTENTE;
        }
        return "COMPLETADA".equals(estados.get(0))
                ? ResultadoConfirmacion.YA_COMPLETADA : ResultadoConfirmacion.ESTADO_INCOMPATIBLE;
    }
}
