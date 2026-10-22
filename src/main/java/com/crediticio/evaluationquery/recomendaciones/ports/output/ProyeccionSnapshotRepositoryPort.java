package com.crediticio.evaluationquery.recomendaciones.ports.output;

import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacion;

public interface ProyeccionSnapshotRepositoryPort {

    /** Inserta el snapshot si es nuevo; un reintento debe coincidir exactamente con el ya persistido. */
    void guardarSiNoExisteOIgual(SnapshotEvaluacion snapshot);
}
