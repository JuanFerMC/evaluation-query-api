package com.crediticio.evaluationquery.historial.infrastructure.output;

import com.crediticio.evaluationquery.historial.domain.EvaluacionHistorica;
import com.crediticio.evaluationquery.historial.domain.FiltroHistorial;
import com.crediticio.evaluationquery.historial.domain.Paginacion;
import com.crediticio.evaluationquery.historial.ports.output.BusquedaHistorialPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * US 5.1.2: lee con SELECT las tablas de scoring-api. La página y el conteo usan la misma cláusula WHERE,
 * construida una sola vez por filtro, con parámetros enlazados. La paginación se hace en la base (LIMIT/OFFSET):
 * nunca se carga el historial completo. El puntaje derivado de las evaluaciones anteriores a HU12 (P-HU15-01) se
 * calcula con una subconsulta por fila de la página, en la misma sentencia (sin N+1). De solicitante solo se
 * compara numero_documento, sin leerlo ni devolverlo (CA13). Excluye las evaluaciones PENDIENTE (CA08).
 */
@Component
public class BusquedaHistorialJdbcAdapter implements BusquedaHistorialPort {

    private static final String CONSULTA_PAGINA = """
            SELECT e.id_evaluacion, e.fecha_evaluacion, e.score_total, e.categoria, e.estado,
                   p.version AS version_politica,
                   (SELECT COALESCE(SUM(d.puntaje_obtenido), 0)
                    FROM detalle_evaluacion d
                    WHERE d.id_evaluacion = e.id_evaluacion) AS suma_detalles
            FROM evaluacion e
            LEFT JOIN politica_clasificacion p ON p.id_politica = e.id_politica
            %s
            ORDER BY e.fecha_evaluacion DESC, e.id_evaluacion DESC
            LIMIT :limite OFFSET :desplazamiento
            """;

    private static final String CONSULTA_CONTEO = """
            SELECT COUNT(*)
            FROM evaluacion e
            %s
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BusquedaHistorialJdbcAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<EvaluacionHistorica> buscar(FiltroHistorial filtro, Paginacion paginacion) {
        Condiciones condiciones = Condiciones.de(filtro);
        MapSqlParameterSource parametros = condiciones.parametros()
                .addValue("limite", paginacion.tamano())
                .addValue("desplazamiento", paginacion.desplazamiento());
        return jdbcTemplate.query(CONSULTA_PAGINA.formatted(condiciones.where()), parametros,
                FilaEvaluacionHistorica.MAPEADOR);
    }

    @Override
    public long contar(FiltroHistorial filtro) {
        Condiciones condiciones = Condiciones.de(filtro);
        Long total = jdbcTemplate.queryForObject(CONSULTA_CONTEO.formatted(condiciones.where()),
                condiciones.parametros(), Long.class);
        return total == null ? 0 : total;
    }

    /** Cláusula WHERE y parámetros de un filtro; solo incluye las condiciones de los filtros informados. */
    private record Condiciones(String where, MapSqlParameterSource parametros) {

        static Condiciones de(FiltroHistorial filtro) {
            StringBuilder where = new StringBuilder("WHERE (e.estado IS NULL OR e.estado <> 'PENDIENTE')");
            MapSqlParameterSource parametros = new MapSqlParameterSource();
            if (filtro.inicioInclusivo() != null) {
                where.append("\n  AND e.fecha_evaluacion >= :inicio");
                parametros.addValue("inicio", filtro.inicioInclusivo());
            }
            if (filtro.finExclusivo() != null) {
                where.append("\n  AND e.fecha_evaluacion < :finExclusivo");
                parametros.addValue("finExclusivo", filtro.finExclusivo());
            }
            if (filtro.numeroDocumento() != null) {
                where.append("\n  AND e.id_solicitante IN (SELECT s.id_solicitante FROM solicitante s "
                        + "WHERE s.numero_documento = :numeroDocumento)");
                parametros.addValue("numeroDocumento", filtro.numeroDocumento());
            }
            if (filtro.categoria() != null) {
                where.append("\n  AND e.categoria = :categoria");
                parametros.addValue("categoria", filtro.categoria().name());
            }
            return new Condiciones(where.toString(), parametros);
        }
    }
}
