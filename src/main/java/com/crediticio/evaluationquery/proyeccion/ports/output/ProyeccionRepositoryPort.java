package com.crediticio.evaluationquery.proyeccion.ports.output;

public interface ProyeccionRepositoryPort {

    void registrarSolicitante(long idSolicitante);

    ResultadoConfirmacion completarEvaluacion(long idEvaluacion);

    enum ResultadoConfirmacion {
        ACTUALIZADA,
        YA_COMPLETADA,
        INEXISTENTE,
        ESTADO_INCOMPATIBLE
    }
}
