package com.crediticio.evaluationquery.historial.infrastructure.input;

import com.crediticio.evaluationquery.historial.application.dto.PaginaHistorialResponse;
import com.crediticio.evaluationquery.historial.domain.FiltroHistorial;
import com.crediticio.evaluationquery.historial.domain.Paginacion;
import com.crediticio.evaluationquery.historial.ports.input.FiltrarHistorialUseCase;
import com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo;
import com.crediticio.evaluationquery.shared.response.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** US 5.1.2: historial de evaluaciones filtrado por fechas, documento del solicitante y nivel de riesgo. Roles: SecurityConfig. */
@RestController
public class BusquedaHistorialController {

    private final FiltrarHistorialUseCase filtrarHistorialUseCase;

    public BusquedaHistorialController(FiltrarHistorialUseCase filtrarHistorialUseCase) {
        this.filtrarHistorialUseCase = filtrarHistorialUseCase;
    }

    @Operation(summary = "Filtrar el historial de evaluaciones",
            description = "Evaluaciones (sin las PENDIENTE) que cumplen todos los filtros indicados, de la más "
                    + "reciente a la más antigua (a igual fecha, por identificador descendente), paginadas y con el "
                    + "total de coincidencias. Todos los filtros son opcionales. Sin coincidencias responde 200 con "
                    + "una lista vacía y total 0. Roles: ADMINISTRADOR_RIESGO y ANALISTA_CREDITO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página del historial (puede estar vacía)",
                    content = @Content(schema = @Schema(implementation = PaginaHistorialResponse.class))),
            @ApiResponse(responseCode = "400", description = "Filtro o paginación no válidos (VALIDATION_ERROR)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Error interno (INTERNAL_ERROR)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/api/v1/evaluaciones")
    public ResponseEntity<PaginaHistorialResponse> filtrar(
            @Parameter(description = "Primer día incluido (aaaa-mm-dd)", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @Parameter(description = "Último día incluido completo (aaaa-mm-dd)", example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @Parameter(description = "Número de documento del solicitante (coincidencia exacta)")
            @RequestParam(required = false) String numeroDocumento,
            @Parameter(description = "Nivel de riesgo persistido: BAJO, MEDIO o ALTO")
            @RequestParam(required = false) CategoriaRiesgo categoria,
            @Parameter(description = "Número de página, desde 0")
            @RequestParam(defaultValue = "0") int pagina,
            @Parameter(description = "Tamaño de página, de 1 a " + Paginacion.TAMANO_MAXIMO)
            @RequestParam(defaultValue = "20") int tamano) {
        FiltroHistorial filtro = new FiltroHistorial(fechaDesde, fechaHasta, numeroDocumento, categoria);
        return ResponseEntity.ok(filtrarHistorialUseCase.filtrar(filtro, new Paginacion(pagina, tamano)));
    }
}
