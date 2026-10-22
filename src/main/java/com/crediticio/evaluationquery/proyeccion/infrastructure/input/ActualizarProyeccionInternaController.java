package com.crediticio.evaluationquery.proyeccion.infrastructure.input;

import com.crediticio.evaluationquery.proyeccion.ports.input.ActualizarProyeccionUseCase;
import com.crediticio.evaluationquery.shared.exception.IdentificadorInvalidoException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operaciones privadas del outbox Scoring; no pertenecen al contrato del frontend. */
@Hidden
@RestController
@ConditionalOnProperty(prefix = "evaluationquery.saga", name = "habilitada", havingValue = "true")
public class ActualizarProyeccionInternaController {

    private final ActualizarProyeccionUseCase actualizarProyeccion;

    public ActualizarProyeccionInternaController(ActualizarProyeccionUseCase actualizarProyeccion) {
        this.actualizarProyeccion = actualizarProyeccion;
    }

    @PutMapping("/internal/v1/proyeccion/solicitantes/{idSolicitante}")
    public ResponseEntity<Void> registrarSolicitante(@PathVariable long idSolicitante) {
        IdentificadorInvalidoException.exigirPositivo(idSolicitante, "idSolicitante");
        actualizarProyeccion.registrarSolicitante(idSolicitante);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/internal/v1/proyeccion/evaluaciones/{idEvaluacion}/completada")
    public ResponseEntity<Void> completarEvaluacion(@PathVariable long idEvaluacion) {
        IdentificadorInvalidoException.exigirPositivo(idEvaluacion, "idEvaluacion");
        actualizarProyeccion.completarEvaluacion(idEvaluacion);
        return ResponseEntity.noContent().build();
    }
}
