package com.crediticio.evaluationquery.historial.application.exportacion;

import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import com.crediticio.evaluationquery.historial.ports.input.ConsultarHistorialExportacionUseCase;
import com.crediticio.evaluationquery.historial.ports.input.ConsultarHistorialUseCase;
import com.crediticio.evaluationquery.historial.ports.output.SolicitanteNombrePort;
import com.crediticio.evaluationquery.shared.exception.IdentificadorInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * Prepara los historiales completos para exportación reutilizando la consulta HU15.
 * Una lista con un identificador equivale a una exportación individual.
 */
@Service
public class ConsultarHistorialExportacionService
        implements ConsultarHistorialExportacionUseCase {

    private static final int MAX_SOLICITANTES_POR_EXPORTACION = 50;

    private final ConsultarHistorialUseCase consultarHistorialUseCase;
    private final SolicitanteNombrePort solicitanteNombrePort;

    public ConsultarHistorialExportacionService(
            ConsultarHistorialUseCase consultarHistorialUseCase,
            SolicitanteNombrePort solicitanteNombrePort) {
        this.consultarHistorialUseCase = consultarHistorialUseCase;
        this.solicitanteNombrePort = solicitanteNombrePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialSolicitanteExportacion> consultar(List<Long> solicitanteIds) {
        validarSolicitantes(solicitanteIds);

        return solicitanteIds.stream()
                .map(idSolicitante -> new HistorialSolicitanteExportacion(
                        idSolicitante,
                        solicitanteNombrePort.obtenerNombreCompleto(idSolicitante),
                        consultarHistorialUseCase.consultar(idSolicitante)))
                .toList();
    }

    private void validarSolicitantes(List<Long> solicitanteIds) {
        if (solicitanteIds == null || solicitanteIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un solicitante");
        }

        if (solicitanteIds.size() > MAX_SOLICITANTES_POR_EXPORTACION) {
            throw new IllegalArgumentException(
                    "No se pueden exportar más de " + MAX_SOLICITANTES_POR_EXPORTACION
                            + " solicitantes en una solicitud");
        }

        if (solicitanteIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IdentificadorInvalidoException("solicitanteIds");
        }

        if (new HashSet<>(solicitanteIds).size() != solicitanteIds.size()) {
            throw new IllegalArgumentException(
                    "No se permiten identificadores de solicitantes duplicados");
        }
    }
}
