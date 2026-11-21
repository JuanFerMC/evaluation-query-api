package com.crediticio.evaluationquery.reportes.infrastructure.output;

import com.crediticio.evaluationquery.reportes.domain.DistribucionRiesgo;
import com.crediticio.evaluationquery.reportes.ports.output.DistribucionRiesgoPort;
import com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * US 7.1.1: una sola sentencia agregada sobre evaluacion (SELECT en la base de scoring-api): la base agrupa y
 * cuenta, sin traer las evaluaciones a memoria, y las cantidades y el total salen de la misma lectura. Sin joins,
 * cada fila de evaluacion se cuenta una vez. Incluye todos los estados de la saga: la categoría de una evaluación
 * PENDIENTE ya está persistida y la nueva evaluación debe reflejarse en la consulta siguiente (escenario 2). La
 * categoría NULL corresponde a las evaluaciones anteriores a HU12. Una categoría fuera de BAJO/MEDIO/ALTO es un
 * dato incoherente: no se reinterpreta y la consulta falla (500).
 */
@Component
public class DistribucionRiesgoJdbcAdapter implements DistribucionRiesgoPort {

    private static final String CONTEO_POR_CATEGORIA = """
            SELECT e.categoria, COUNT(*) AS cantidad
            FROM evaluacion e
            GROUP BY e.categoria
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DistribucionRiesgoJdbcAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DistribucionRiesgo contarPorCategoria() {
        Map<CategoriaRiesgo, Long> cantidades = new EnumMap<>(CategoriaRiesgo.class);
        long[] sinClasificar = {0};
        jdbcTemplate.query(CONTEO_POR_CATEGORIA, Map.of(), fila -> {
            String categoria = fila.getString("categoria");
            long cantidad = fila.getLong("cantidad");
            if (categoria == null) {
                sinClasificar[0] = cantidad;
            } else {
                cantidades.put(categoriaPersistida(categoria), cantidad);
            }
        });
        return DistribucionRiesgo.de(cantidades, sinClasificar[0]);
    }

    private static CategoriaRiesgo categoriaPersistida(String categoria) {
        try {
            return CategoriaRiesgo.valueOf(categoria);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Categoría de riesgo persistida no reconocida: " + categoria, ex);
        }
    }
}
