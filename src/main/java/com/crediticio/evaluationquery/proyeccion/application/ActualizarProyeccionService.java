package com.crediticio.evaluationquery.proyeccion.application;

import com.crediticio.evaluationquery.proyeccion.domain.EvaluacionProyectadaNoEncontradaException;
import com.crediticio.evaluationquery.proyeccion.domain.EstadoProyeccionIncompatibleException;
import com.crediticio.evaluationquery.proyeccion.ports.input.ActualizarProyeccionUseCase;
import com.crediticio.evaluationquery.proyeccion.ports.output.ProyeccionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActualizarProyeccionService implements ActualizarProyeccionUseCase {

    private final ProyeccionRepositoryPort repositorio;

    public ActualizarProyeccionService(ProyeccionRepositoryPort repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public void registrarSolicitante(long idSolicitante) {
        repositorio.registrarSolicitante(idSolicitante);
    }

    @Override
    @Transactional
    public void completarEvaluacion(long idEvaluacion) {
        switch (repositorio.completarEvaluacion(idEvaluacion)) {
            case ACTUALIZADA, YA_COMPLETADA -> { }
            case INEXISTENTE -> throw new EvaluacionProyectadaNoEncontradaException();
            case ESTADO_INCOMPATIBLE -> throw new EstadoProyeccionIncompatibleException();
        }
    }
}
