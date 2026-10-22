package com.crediticio.evaluationquery.proyeccion.domain;

public class EvaluacionProyectadaNoEncontradaException extends RuntimeException {

    public EvaluacionProyectadaNoEncontradaException() {
        super("La evaluación todavía no existe en la proyección de lectura");
    }
}
