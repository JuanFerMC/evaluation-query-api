package com.crediticio.evaluationquery.recomendaciones.domain;

public class SnapshotEvaluacionInconsistenteException extends RuntimeException {

    public SnapshotEvaluacionInconsistenteException(long idEvaluacion) {
        super("El snapshot recibido no coincide con el ya guardado para la evaluación " + idEvaluacion);
    }
}
