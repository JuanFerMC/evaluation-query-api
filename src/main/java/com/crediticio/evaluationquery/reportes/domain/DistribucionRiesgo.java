package com.crediticio.evaluationquery.reportes.domain;

import com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/**
 * US 7.1.1: distribución de las evaluaciones por nivel de riesgo persistido. La unidad de conteo es la evaluación
 * (decisión del 2026-11-20): un solicitante con varias evaluaciones aporta una por cada una. El total es la suma de
 * las tres categorías; las evaluaciones sin clasificación (anteriores a HU12, sin categoría persistida) no forman
 * parte de la distribución y se informan aparte. Los porcentajes se calculan sobre el total con dos decimales
 * (HALF_UP), por lo que su suma puede diferir de 100 en ±0,01 por redondeo; con total 0 son 0.
 */
public final class DistribucionRiesgo {

    private static final int DECIMALES = 2;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final Map<CategoriaRiesgo, Long> cantidades;
    private final long sinClasificar;

    private DistribucionRiesgo(Map<CategoriaRiesgo, Long> cantidades, long sinClasificar) {
        this.cantidades = cantidades;
        this.sinClasificar = sinClasificar;
    }

    /**
     * @param cantidadesPorCategoria evaluaciones por categoría; una categoría ausente cuenta 0
     * @param sinClasificar          evaluaciones sin categoría persistida
     */
    public static DistribucionRiesgo de(Map<CategoriaRiesgo, Long> cantidadesPorCategoria, long sinClasificar) {
        EnumMap<CategoriaRiesgo, Long> cantidades = new EnumMap<>(CategoriaRiesgo.class);
        for (CategoriaRiesgo categoria : CategoriaRiesgo.values()) {
            long cantidad = cantidadesPorCategoria.getOrDefault(categoria, 0L);
            exigirNoNegativa(cantidad, categoria.name());
            cantidades.put(categoria, cantidad);
        }
        exigirNoNegativa(sinClasificar, "sin clasificar");
        return new DistribucionRiesgo(cantidades, sinClasificar);
    }

    public long cantidad(CategoriaRiesgo categoria) {
        return cantidades.get(categoria);
    }

    /** Total de evaluaciones consideradas en la distribución: exactamente la suma de las categorías. */
    public long total() {
        return cantidades.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Porcentaje de la categoría sobre el total, con dos decimales; 0.00 si no hay evaluaciones. */
    public BigDecimal porcentaje(CategoriaRiesgo categoria) {
        long total = total();
        if (total == 0) {
            return BigDecimal.ZERO.setScale(DECIMALES);
        }
        return BigDecimal.valueOf(cantidad(categoria)).multiply(CIEN)
                .divide(BigDecimal.valueOf(total), DECIMALES, RoundingMode.HALF_UP);
    }

    public long getSinClasificar() {
        return sinClasificar;
    }

    private static void exigirNoNegativa(long cantidad, String concepto) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("Cantidad negativa para " + concepto);
        }
    }
}
