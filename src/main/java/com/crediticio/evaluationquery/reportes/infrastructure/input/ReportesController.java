package com.crediticio.evaluationquery.reportes.infrastructure.input;

import com.crediticio.evaluationquery.reportes.application.dto.DistribucionRiesgoResponse;
import com.crediticio.evaluationquery.reportes.ports.input.ConsultarDistribucionRiesgoUseCase;
import com.crediticio.evaluationquery.shared.response.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** US 7.1.1: reportes de riesgo. Roles: SecurityConfig. */
@RestController
public class ReportesController {

    private final ConsultarDistribucionRiesgoUseCase consultarDistribucionRiesgoUseCase;

    public ReportesController(ConsultarDistribucionRiesgoUseCase consultarDistribucionRiesgoUseCase) {
        this.consultarDistribucionRiesgoUseCase = consultarDistribucionRiesgoUseCase;
    }

    @Operation(summary = "Consultar la distribución de evaluaciones por nivel de riesgo",
            description = "Cantidad y porcentaje (dos decimales) de evaluaciones BAJO, MEDIO y ALTO, y el total "
                    + "consolidado (suma de las tres). Cada evaluación cuenta una vez: un solicitante con varias "
                    + "evaluaciones aporta una por cada una. Las evaluaciones sin clasificación (anteriores a HU12) "
                    + "se informan aparte y no entran en el total. Se calcula en cada consulta. Roles: "
                    + "ADMINISTRADOR_RIESGO y ANALISTA_CREDITO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Distribución (todo en 0 si no hay evaluaciones)",
                    content = @Content(schema = @Schema(implementation = DistribucionRiesgoResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno (INTERNAL_ERROR)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/api/v1/reportes/distribucion-riesgo")
    public ResponseEntity<DistribucionRiesgoResponse> consultarDistribucionRiesgo() {
        return ResponseEntity.ok(consultarDistribucionRiesgoUseCase.consultar());
    }
}
