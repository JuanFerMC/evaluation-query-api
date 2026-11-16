package com.crediticio.evaluationquery.historial.application.exportacion.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Identificadores de los solicitantes cuyos historiales completos se exportarán.
 * Una lista de un elemento representa una exportación individual.
 */
public record SolicitudExportacionHistorial(
        @NotEmpty(message = "Debe seleccionar al menos un solicitante")
        @Size(max = 50, message = "No se pueden seleccionar más de 50 solicitantes")
        List<@NotNull(message = "El identificador del solicitante es obligatorio")
                @Positive(message = "Los identificadores deben ser positivos") Long> solicitanteIds) {
}
