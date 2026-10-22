package com.crediticio.evaluationquery.shared.exception;

import com.crediticio.evaluationquery.explicacion.domain.EvaluacionNoEncontradaException;
import com.crediticio.evaluationquery.explicacion.domain.ExplicacionInconsistenteException;
import com.crediticio.evaluationquery.historial.domain.SolicitanteNoEncontradoException;
import com.crediticio.evaluationquery.proyeccion.domain.EvaluacionProyectadaNoEncontradaException;
import com.crediticio.evaluationquery.proyeccion.domain.EstadoProyeccionIncompatibleException;
import com.crediticio.evaluationquery.recomendaciones.domain.CatalogoNoDisponibleException;
import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionInexistenteException;
import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionNoPendienteException;
import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacionInconsistenteException;
import com.crediticio.evaluationquery.recomendaciones.domain.SnapshotEvaluacionInvalidoException;
import com.crediticio.evaluationquery.shared.response.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Errores de negocio con el formato ApiError y el traceId de X-Trace-Id (contrato común de las tres API).
 * Los códigos EVALUACION_NO_ENCONTRADA y SOLICITANTE_NO_ENCONTRADO son los de scoring-api (P-HU13-07,
 * P-HU15-05); EVALUACION_PENDIENTE, EVALUACION_NO_PENDIENTE y CATALOGO_NO_DISPONIBLE son los de la saga de
 * HU14 (P-HU13-02, P-HU14-03, P-HU14-08). Las respuestas 401 y 403 las escribe RespuestaSeguridadJson.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EvaluacionNoEncontradaException.class)
    public ResponseEntity<ApiError> manejarEvaluacionNoEncontrada(EvaluacionNoEncontradaException ex) {
        log.warn("Consulta de una evaluación que no existe");
        return construirRespuesta(HttpStatus.NOT_FOUND, "EVALUACION_NO_ENCONTRADA", ex.getMessage());
    }

    @ExceptionHandler(EvaluacionInexistenteException.class)
    public ResponseEntity<ApiError> manejarEvaluacionInexistente(EvaluacionInexistenteException ex) {
        log.warn("Recomendaciones de una evaluación que no existe");
        return construirRespuesta(HttpStatus.NOT_FOUND, "EVALUACION_NO_ENCONTRADA", ex.getMessage());
    }

    @ExceptionHandler(EvaluacionProyectadaNoEncontradaException.class)
    public ResponseEntity<ApiError> manejarEvaluacionProyectadaNoEncontrada(
            EvaluacionProyectadaNoEncontradaException ex) {
        log.info("Confirmación recibida antes de que la evaluación exista en la proyección");
        return construirRespuesta(HttpStatus.NOT_FOUND, "EVALUACION_PROYECCION_NO_ENCONTRADA", ex.getMessage());
    }

    @ExceptionHandler(EstadoProyeccionIncompatibleException.class)
    public ResponseEntity<ApiError> manejarEstadoProyeccionIncompatible(EstadoProyeccionIncompatibleException ex) {
        log.warn("Se rechazó una transición de estado incompatible en la proyección");
        return construirRespuesta(HttpStatus.CONFLICT, "ESTADO_PROYECCION_INCOMPATIBLE", ex.getMessage());
    }

    /** La evaluación sigue PENDIENTE en la saga: todavía no es una evaluación completa (HU14 CA13). */
    @ExceptionHandler(EvaluacionPendienteException.class)
    public ResponseEntity<ApiError> manejarEvaluacionPendiente(EvaluacionPendienteException ex) {
        log.info("Consulta de una evaluación pendiente");
        return construirRespuesta(HttpStatus.CONFLICT, "EVALUACION_PENDIENTE", ex.getMessage());
    }

    /** Generación interna para una evaluación que no está PENDIENTE (anterior a la saga): rechazo permanente. */
    @ExceptionHandler(EvaluacionNoPendienteException.class)
    public ResponseEntity<ApiError> manejarEvaluacionNoPendiente(EvaluacionNoPendienteException ex) {
        log.warn("Generación de recomendaciones rechazada: la evaluación no está pendiente");
        return construirRespuesta(HttpStatus.CONFLICT, "EVALUACION_NO_PENDIENTE", ex.getMessage());
    }

    @ExceptionHandler(SnapshotEvaluacionInvalidoException.class)
    public ResponseEntity<ApiError> manejarSnapshotInvalido(SnapshotEvaluacionInvalidoException ex) {
        log.warn("Se rechazó un snapshot de evaluación que no cumple el contrato");
        return construirRespuesta(HttpStatus.BAD_REQUEST, "SNAPSHOT_EVALUACION_INVALIDO", ex.getMessage());
    }

    @ExceptionHandler(SnapshotEvaluacionInconsistenteException.class)
    public ResponseEntity<ApiError> manejarSnapshotInconsistente(SnapshotEvaluacionInconsistenteException ex) {
        log.error(ex.getMessage());
        return construirRespuesta(HttpStatus.CONFLICT, "SNAPSHOT_EVALUACION_INCONSISTENTE", ex.getMessage());
    }

    /** Sin un catálogo APROBADO no se generan recomendaciones; la saga lo trata como transitorio y reintenta. */
    @ExceptionHandler(CatalogoNoDisponibleException.class)
    public ResponseEntity<ApiError> manejarCatalogoNoDisponible(CatalogoNoDisponibleException ex) {
        log.warn(ex.getMessage());
        return construirRespuesta(HttpStatus.SERVICE_UNAVAILABLE, "CATALOGO_NO_DISPONIBLE",
                "El catálogo de recomendaciones no está disponible");
    }

    @ExceptionHandler(SolicitanteNoEncontradoException.class)
    public ResponseEntity<ApiError> manejarSolicitanteNoEncontrado(SolicitanteNoEncontradoException ex) {
        log.warn("Consulta del historial de un solicitante que no existe");
        return construirRespuesta(HttpStatus.NOT_FOUND, "SOLICITANTE_NO_ENCONTRADO", ex.getMessage());
    }

    @ExceptionHandler(IdentificadorInvalidoException.class)
    public ResponseEntity<ApiError> manejarIdentificadorInvalido(IdentificadorInvalidoException ex) {
        log.warn("Solicitud rechazada por un identificador no positivo");
        return construirRespuesta(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> manejarTipoDeArgumentoInvalido(MethodArgumentTypeMismatchException ex) {
        log.warn("Solicitud rechazada por un parámetro con formato inválido");
        return construirRespuesta(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "El parámetro '" + ex.getName() + "' tiene un formato inválido");
    }

    /** P-HU13-05: datos persistidos incoherentes; se registran y no se corrigen ni se ocultan. */
    @ExceptionHandler(ExplicacionInconsistenteException.class)
    public ResponseEntity<ApiError> manejarExplicacionInconsistente(ExplicacionInconsistenteException ex) {
        log.error(ex.getMessage());
        return errorInterno();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> manejarErrorInterno(Exception ex) {
        log.error("Error interno no controlado", ex);
        return errorInterno();
    }

    private static ResponseEntity<ApiError> errorInterno() {
        return construirRespuesta(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocurrió un error interno. Intente nuevamente más tarde");
    }

    private static ResponseEntity<ApiError> construirRespuesta(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status).body(new ApiError(errorCode, message, List.of(), MDC.get("traceId")));
    }
}
