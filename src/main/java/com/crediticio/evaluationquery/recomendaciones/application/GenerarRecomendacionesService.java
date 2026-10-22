package com.crediticio.evaluationquery.recomendaciones.application;

import com.crediticio.evaluationquery.recomendaciones.application.dto.ResultadoGeneracionResponse;
import com.crediticio.evaluationquery.recomendaciones.domain.GeneracionConcurrenteException;
import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacion;
import com.crediticio.evaluationquery.recomendaciones.ports.input.GenerarRecomendacionesUseCase;
import org.springframework.stereotype.Service;

/**
 * Generación idempotente de recomendaciones (HU14 CA06, CA14). Si dos reintentos de la misma evaluación compiten,
 * la segunda inserción choca con la clave primaria de la cabecera; en lugar de fallar, una transacción nueva lee y
 * devuelve la generación ya guardada.
 */
@Service
public class GenerarRecomendacionesService implements GenerarRecomendacionesUseCase {

    private final GeneracionRecomendacionesTransaccional transaccional;
    private final RecomendacionesMapper mapper;

    public GenerarRecomendacionesService(GeneracionRecomendacionesTransaccional transaccional,
            RecomendacionesMapper mapper) {
        this.transaccional = transaccional;
        this.mapper = mapper;
    }

    @Override
    public ResultadoGeneracionResponse generar(long idEvaluacion) {
        try {
            GeneracionRecomendacionesTransaccional.Resultado resultado = transaccional.generar(idEvaluacion);
            return mapper.aResultado(resultado.generacion(), resultado.creada());
        } catch (GeneracionConcurrenteException ex) {
            return mapper.aResultado(transaccional.existente(idEvaluacion).orElseThrow(() -> ex), false);
        }
    }

    @Override
    public ResultadoGeneracionResponse generar(SnapshotEvaluacion snapshot) {
        try {
            GeneracionRecomendacionesTransaccional.Resultado resultado = transaccional.generar(snapshot);
            return mapper.aResultado(resultado.generacion(), resultado.creada());
        } catch (GeneracionConcurrenteException ex) {
            return mapper.aResultado(transaccional.existente(snapshot.idEvaluacion()).orElseThrow(() -> ex), false);
        }
    }
}
