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
 * US 5.1.2: lee con SELECT las tablas de scoring-api. La página y el conteo usan las mismas condiciones
 * estáticas y parámetros enlazados. La paginación se hace en la base (LIMIT/OFFSET): nunca se carga el
 * historial completo. El puntaje derivado de las evaluaciones anteriores a HU12 (P-HU15-01) se calcula con
 * una subconsulta por fila de la página, en la misma sentencia (sin N+1). De solicitante solo se compara
 * numero_documento, sin leerlo ni devolverlo (CA13). Excluye las evaluaciones PENDIENTE (CA08).
 */
@Component
public class BusquedaHistorialJdbcAdapter implements BusquedaHistorialPort {

    private static final String CONDICIONES_WHERE = """
            WHERE (e.estado IS NULL OR e.estado <> 'PENDIENTE')
              AND (:usarInicio = false OR e.fecha_evaluacion >= :inicio)
              AND (:usarFin = false OR e.fecha_evaluacion < :finExclusivo)
              AND (:usarDocumento = false OR e.id_solicitante IN (
                    SELECT s.id_solicitante
                    FROM solicitante s
                    WHERE s.numero_documento = :numeroDocumento
              ))
              AND (:usarCategoria = false OR e.categoria = :categoria)
            """;

    private static final String CONSULTA_PAGINA = """
            SELECT e.id_evaluacion, e.fecha_evaluacion, e.score_total, e.categoria, e.estado,
                   p.version AS version_politica,
                   (SELECT COALESCE(SUM(d.puntaje_obtenido), 0)
                    FROM detalle_evaluacion d
                    WHERE d.id_evaluacion = e.id_evaluacion) AS suma_detalles
            FROM evaluacion e
            LEFT JOIN politica_clasificacion p ON p.id_politica = e.id_politica
            """ + CONDICIONES_WHERE + """
            ORDER BY e.fecha_evaluacion DESC, e.id_evaluacion DESC
            LIMIT :limite OFFSET :desplazamiento
            """;

    private static final String CONSULTA_CONTEO = """
            SELECT COUNT(*)
            FROM evaluacion e
            """ + CONDICIONES_WHERE;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BusquedaHistorialJdbcAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<EvaluacionHistorica> buscar(FiltroHistorial filtro, Paginacion paginacion) {
        MapSqlParameterSource parametros = parametros(filtro)
                .addValue("limite", paginacion.tamano())
                .addValue("desplazamiento", paginacion.desplazamiento());
        return jdbcTemplate.query(CONSULTA_PAGINA, parametros, FilaEvaluacionHistorica.MAPEADOR);
    }

    @Override
    public long contar(FiltroHistorial filtro) {
        Long total = jdbcTemplate.queryForObject(CONSULTA_CONTEO, parametros(filtro), Long.class);
        return total == null ? 0 : total;
    }

    /**
     * Los valores opcionales se enlazan como parámetros; los booleanos activan o desactivan cada filtro.
     * Ningún dato del usuario se concatena ni se interpola en la sentencia SQL.
     */
    private static MapSqlParameterSource parametros(FiltroHistorial filtro) {
        return new MapSqlParameterSource()
                .addValue("usarInicio", filtro.inicioInclusivo() != null)
                .addValue("inicio", filtro.inicioInclusivo())
                .addValue("usarFin", filtro.finExclusivo() != null)
                .addValue("finExclusivo", filtro.finExclusivo())
                .addValue("usarDocumento", filtro.numeroDocumento() != null)
                .addValue("numeroDocumento", filtro.numeroDocumento())
                .addValue("usarCategoria", filtro.categoria() != null)
                .addValue("categoria", filtro.categoria() == null ? null : filtro.categoria().name());
    }
}
