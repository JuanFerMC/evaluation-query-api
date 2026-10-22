package com.crediticio.evaluationquery.proyeccion.domain;

public class EstadoProyeccionIncompatibleException extends RuntimeException {

    public EstadoProyeccionIncompatibleException() {
        super("La evaluación no puede cambiar a COMPLETADA desde su estado actual");
    }
}
