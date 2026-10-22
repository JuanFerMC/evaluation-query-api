package com.crediticio.evaluationquery.recomendaciones.domain;

import java.util.List;

/**
 * Datos de una evaluación que necesita HU14: estado de la saga (null antes de la saga), clasificación y factores.
 * Los nuevos snapshots se reciben de Scoring; el adaptador de origen se conserva temporalmente para consultas
 * públicas y compatibilidad durante el despliegue gradual.
 */
public record EvaluacionParaRecomendar(long idEvaluacion, String estado, String categoria,
        List<FactorEvaluado> factores) {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String COMPLETADA = "COMPLETADA";

    public EvaluacionParaRecomendar {
        factores = List.copyOf(factores);
    }

    public boolean esPendiente() {
        return PENDIENTE.equals(estado);
    }

    public boolean esAnteriorALaSaga() {
        return estado == null;
    }
}
