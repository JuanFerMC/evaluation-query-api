package com.crediticio.evaluationquery.recomendaciones.ports.input;

import com.crediticio.evaluationquery.recomendaciones.application.dto.ResultadoGeneracionResponse;
import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacion;

public interface GenerarRecomendacionesUseCase {

    ResultadoGeneracionResponse generar(long idEvaluacion);

    ResultadoGeneracionResponse generar(SnapshotEvaluacion snapshot);
}
