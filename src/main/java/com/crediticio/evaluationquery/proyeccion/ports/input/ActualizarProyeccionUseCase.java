package com.crediticio.evaluationquery.proyeccion.ports.input;

public interface ActualizarProyeccionUseCase {

    void registrarSolicitante(long idSolicitante);

    void completarEvaluacion(long idEvaluacion);
}
