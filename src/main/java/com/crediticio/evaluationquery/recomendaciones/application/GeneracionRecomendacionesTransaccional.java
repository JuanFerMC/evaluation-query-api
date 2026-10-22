package com.crediticio.evaluationquery.recomendaciones.application;

import com.crediticio.evaluationquery.recomendaciones.domain.CatalogoNoDisponibleException;
import com.crediticio.evaluationquery.recomendaciones.domain.CatalogoRecomendaciones;
import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionInexistenteException;
import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionNoPendienteException;
import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionParaRecomendar;
import com.crediticio.evaluationquery.recomendaciones.domain.FactorEvaluado;
import com.crediticio.evaluationquery.recomendaciones.domain.GeneracionRecomendaciones;
import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacion;
import com.crediticio.evaluationquery.recomendaciones.ports.output.CatalogoRecomendacionesPort;
import com.crediticio.evaluationquery.recomendaciones.ports.output.EvaluacionParaRecomendarPort;
import com.crediticio.evaluationquery.recomendaciones.ports.output.ProyeccionSnapshotRepositoryPort;
import com.crediticio.evaluationquery.recomendaciones.ports.output.RecomendacionesRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Transacciones locales de la generación (HU14). La saga de scoring-api la invoca; esta API no confirma nada en
 * scoring-api: cada API tiene su propia transacción y no existe una transacción distribuida.
 */
@Service
public class GeneracionRecomendacionesTransaccional {

    private final EvaluacionParaRecomendarPort evaluacionPort;
    private final RecomendacionesRepositoryPort repositorio;
    private final CatalogoRecomendacionesPort catalogoPort;
    private final ProyeccionSnapshotRepositoryPort proyeccionPort;

    public GeneracionRecomendacionesTransaccional(EvaluacionParaRecomendarPort evaluacionPort,
            RecomendacionesRepositoryPort repositorio, CatalogoRecomendacionesPort catalogoPort,
            ProyeccionSnapshotRepositoryPort proyeccionPort) {
        this.evaluacionPort = evaluacionPort;
        this.repositorio = repositorio;
        this.catalogoPort = catalogoPort;
        this.proyeccionPort = proyeccionPort;
    }

    /**
     * Si ya existe una generación la devuelve (reintento, CA06, CA15); si no, exige que la evaluación exista y esté
     * PENDIENTE, aplica el catálogo APROBADO a su clasificación y factores persistidos (CA02, CA09) y la guarda.
     *
     * @return la generación y si se creó en esta llamada
     */
    @Transactional
    public Resultado generar(long idEvaluacion) {
        Optional<GeneracionRecomendaciones> existente = repositorio.buscar(idEvaluacion);
        if (existente.isPresent()) {
            return new Resultado(existente.get(), false);
        }
        EvaluacionParaRecomendar evaluacion = evaluacionPort.buscar(idEvaluacion)
                .orElseThrow(EvaluacionInexistenteException::new);
        return generarDesdeEvaluacion(evaluacion);
    }

    /** Persiste el snapshot recibido y genera desde ese mismo contenido, sin leer tablas de scoring-api. */
    @Transactional
    public Resultado generar(SnapshotEvaluacion snapshot) {
        proyeccionPort.guardarSiNoExisteOIgual(snapshot);
        Optional<GeneracionRecomendaciones> existente = repositorio.buscar(snapshot.idEvaluacion());
        if (existente.isPresent()) {
            return new Resultado(existente.get(), false);
        }
        EvaluacionParaRecomendar evaluacion = new EvaluacionParaRecomendar(snapshot.idEvaluacion(), snapshot.estado(),
                snapshot.categoria(), snapshot.factores().stream()
                        .map(factor -> new FactorEvaluado(factor.variable(), factor.condicionCumplida(),
                                factor.contribucion()))
                        .toList());
        return generarDesdeEvaluacion(evaluacion);
    }

    private Resultado generarDesdeEvaluacion(EvaluacionParaRecomendar evaluacion) {
        if (!evaluacion.esPendiente() || evaluacion.categoria() == null) {
            throw new EvaluacionNoPendienteException();
        }
        CatalogoRecomendaciones catalogo = catalogoPort.catalogo();
        if (!catalogo.isAprobado()) {
            throw new CatalogoNoDisponibleException(catalogo.getVersion());
        }
        GeneracionRecomendaciones generacion = new GeneracionRecomendaciones(evaluacion.idEvaluacion(),
                catalogo.getVersion(), evaluacion.categoria(), null,
                catalogo.seleccionar(evaluacion.categoria(), evaluacion.factores()));
        return new Resultado(repositorio.guardar(generacion), true);
    }

    @Transactional(readOnly = true)
    public Optional<GeneracionRecomendaciones> existente(long idEvaluacion) {
        return repositorio.buscar(idEvaluacion);
    }

    public record Resultado(GeneracionRecomendaciones generacion, boolean creada) {
    }
}
