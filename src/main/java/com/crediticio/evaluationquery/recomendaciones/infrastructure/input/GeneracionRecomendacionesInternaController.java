package com.crediticio.evaluationquery.recomendaciones.infrastructure.input;

import com.crediticio.evaluationquery.recomendaciones.application.dto.ResultadoGeneracionResponse;
import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacion;
import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacionInvalidoException;
import com.crediticio.evaluationquery.recomendaciones.ports.input.GenerarRecomendacionesUseCase;
import com.crediticio.evaluationquery.shared.exception.IdentificadorInvalidoException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU14: operación interna e idempotente que invoca la saga de scoring-api (P-HU14-05). Solo la autoriza el token
 * de servicio S1 (SeguridadServicioConfig); ningún token de usuario. 201 si la generación se crea ahora y 200 si
 * ya existía. Solo existe con evaluationquery.saga.habilitada=true y no forma parte del contrato del frontend.
 */
@Hidden
@RestController
@ConditionalOnProperty(prefix = "evaluationquery.saga", name = "habilitada", havingValue = "true")
public class GeneracionRecomendacionesInternaController {

    private final GenerarRecomendacionesUseCase generarRecomendacionesUseCase;

    public GeneracionRecomendacionesInternaController(GenerarRecomendacionesUseCase generarRecomendacionesUseCase) {
        this.generarRecomendacionesUseCase = generarRecomendacionesUseCase;
    }

    @PutMapping("/internal/v1/evaluaciones/{idEvaluacion}/recomendaciones")
    public ResponseEntity<ResultadoGeneracionResponse> generar(@PathVariable long idEvaluacion,
            @RequestBody(required = false) SnapshotEvaluacion snapshot) {
        IdentificadorInvalidoException.exigirPositivo(idEvaluacion, "idEvaluacion");
        if (snapshot != null && snapshot.idEvaluacion() != idEvaluacion) {
            throw new SnapshotEvaluacionInvalidoException("El ID de la ruta debe coincidir con el ID del snapshot");
        }
        ResultadoGeneracionResponse resultado = snapshot == null
                ? generarRecomendacionesUseCase.generar(idEvaluacion)
                : generarRecomendacionesUseCase.generar(snapshot);
        return ResponseEntity.status(resultado.creada() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado);
    }
}
