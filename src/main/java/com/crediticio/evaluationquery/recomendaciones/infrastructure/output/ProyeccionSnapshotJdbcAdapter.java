package com.crediticio.evaluationquery.recomendaciones.infrastructure.output;

import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacion;
import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacionInconsistenteException;
import com.crediticio.evaluationquery.recomendaciones.ports.output.ProyeccionSnapshotRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.regex.Pattern;

/** Persiste y verifica snapshots en las tablas locales de consulta; no accede a public. */
@Component
public class ProyeccionSnapshotJdbcAdapter implements ProyeccionSnapshotRepositoryPort {

    private static final Pattern IDENTIFICADOR = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String insertarSolicitante;
    private final String insertarEvaluacion;
    private final String insertarFactor;
    private final String consultarEvaluacion;
    private final String consultarFactores;

    public ProyeccionSnapshotJdbcAdapter(NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${evaluationquery.consulta.esquema:consulta}") String esquema) {
        if (esquema == null || !IDENTIFICADOR.matcher(esquema).matches()) {
            throw new IllegalStateException("evaluationquery.consulta.esquema debe ser un identificador en minúsculas");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.insertarSolicitante = "INSERT INTO %s.proyeccion_solicitante (id_solicitante) VALUES (:id) "
                .formatted(esquema) + "ON CONFLICT (id_solicitante) DO NOTHING";
        this.insertarEvaluacion = """
                INSERT INTO %s.proyeccion_evaluacion
                    (id_evaluacion, id_solicitante, fecha_evaluacion, score_total, categoria, version_politica, estado)
                VALUES (:idEvaluacion, :idSolicitante, :fechaEvaluacion, :scoreTotal, :categoria, :versionPolitica, :estado)
                ON CONFLICT (id_evaluacion) DO NOTHING
                """.formatted(esquema);
        this.insertarFactor = """
                INSERT INTO %s.proyeccion_factor_evaluacion
                    (id_evaluacion, orden, id_detalle_origen, id_regla, variable, operador_aplicado,
                     valor_condicion_aplicado, puntaje_regla_aplicado, condicion_cumplida, contribucion)
                VALUES (:idEvaluacion, :orden, :idDetalleOrigen, :idRegla, :variable, :operadorAplicado,
                        :valorCondicionAplicado, :puntajeReglaAplicado, :condicionCumplida, :contribucion)
                """.formatted(esquema);
        this.consultarEvaluacion = """
                SELECT id_evaluacion, id_solicitante, fecha_evaluacion, score_total, categoria,
                       version_politica, estado
                FROM %s.proyeccion_evaluacion WHERE id_evaluacion = :idEvaluacion
                """.formatted(esquema);
        this.consultarFactores = """
                SELECT orden, id_detalle_origen, id_regla, variable, operador_aplicado,
                       valor_condicion_aplicado, puntaje_regla_aplicado, condicion_cumplida, contribucion
                FROM %s.proyeccion_factor_evaluacion
                WHERE id_evaluacion = :idEvaluacion ORDER BY orden
                """.formatted(esquema);
    }

    @Override
    public void guardarSiNoExisteOIgual(SnapshotEvaluacion snapshot) {
        MapSqlParameterSource parametros = parametros(snapshot);
        jdbcTemplate.update(insertarSolicitante, new MapSqlParameterSource("id", snapshot.idSolicitante()));
        if (jdbcTemplate.update(insertarEvaluacion, parametros) == 1) {
            if (!snapshot.factores().isEmpty()) {
                jdbcTemplate.batchUpdate(insertarFactor, snapshot.factores().stream()
                        .map(factor -> parametrosFactor(snapshot.idEvaluacion(), factor))
                        .toArray(MapSqlParameterSource[]::new));
            }
            return;
        }

        SnapshotEvaluacion guardado = consultar(snapshot.idEvaluacion());
        if (!snapshot.equals(guardado)) {
            throw new SnapshotEvaluacionInconsistenteException(snapshot.idEvaluacion());
        }
    }

    private SnapshotEvaluacion consultar(long idEvaluacion) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("idEvaluacion", idEvaluacion);
        List<Cabecera> cabeceras = jdbcTemplate.query(consultarEvaluacion, parametros, (fila, numero) ->
                new Cabecera(fila.getLong("id_evaluacion"), fila.getLong("id_solicitante"),
                        fila.getTimestamp("fecha_evaluacion").toLocalDateTime(), fila.getInt("score_total"),
                        fila.getString("categoria"), fila.getInt("version_politica"), fila.getString("estado")));
        if (cabeceras.isEmpty()) {
            throw new SnapshotEvaluacionInconsistenteException(idEvaluacion);
        }
        List<SnapshotEvaluacion.Factor> factores = jdbcTemplate.query(consultarFactores, parametros,
                (fila, numero) -> new SnapshotEvaluacion.Factor(fila.getInt("orden"),
                        fila.getLong("id_detalle_origen"), fila.getLong("id_regla"), fila.getString("variable"),
                        fila.getString("operador_aplicado"), fila.getString("valor_condicion_aplicado"),
                        fila.getInt("puntaje_regla_aplicado"), fila.getBoolean("condicion_cumplida"),
                        fila.getInt("contribucion")));
        Cabecera c = cabeceras.get(0);
        return new SnapshotEvaluacion(c.idEvaluacion(), c.idSolicitante(), c.fechaEvaluacion(), c.scoreTotal(),
                c.categoria(), c.versionPolitica(), c.estado(), factores);
    }

    private static MapSqlParameterSource parametros(SnapshotEvaluacion snapshot) {
        return new MapSqlParameterSource()
                .addValue("idEvaluacion", snapshot.idEvaluacion())
                .addValue("idSolicitante", snapshot.idSolicitante())
                .addValue("fechaEvaluacion", Timestamp.valueOf(snapshot.fechaEvaluacion()))
                .addValue("scoreTotal", snapshot.scoreTotal())
                .addValue("categoria", snapshot.categoria())
                .addValue("versionPolitica", snapshot.versionPolitica())
                .addValue("estado", snapshot.estado());
    }

    private static MapSqlParameterSource parametrosFactor(long idEvaluacion, SnapshotEvaluacion.Factor factor) {
        return new MapSqlParameterSource()
                .addValue("idEvaluacion", idEvaluacion)
                .addValue("orden", factor.orden())
                .addValue("idDetalleOrigen", factor.idDetalleOrigen())
                .addValue("idRegla", factor.idRegla())
                .addValue("variable", factor.variable())
                .addValue("operadorAplicado", factor.operadorAplicado())
                .addValue("valorCondicionAplicado", factor.valorCondicionAplicado())
                .addValue("puntajeReglaAplicado", factor.puntajeReglaAplicado())
                .addValue("condicionCumplida", factor.condicionCumplida())
                .addValue("contribucion", factor.contribucion());
    }

    private record Cabecera(long idEvaluacion, long idSolicitante, java.time.LocalDateTime fechaEvaluacion,
            int scoreTotal, String categoria, int versionPolitica, String estado) {
    }
}
