package com.crediticio.evaluationquery.historial.infrastructure.input;

import com.crediticio.evaluationquery.historial.application.exportacion.dto.SolicitudExportacionHistorial;
import com.crediticio.evaluationquery.historial.ports.input.ConsultarHistorialExportacionUseCase;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorExcelPort;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorPdfPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluaciones/export")
public class ExportacionHistorialController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ConsultarHistorialExportacionUseCase consultarHistorial;
    private final GeneradorPdfPort generadorPdf;
    private final GeneradorExcelPort generadorExcel;

    public ExportacionHistorialController(
            ConsultarHistorialExportacionUseCase consultarHistorial,
            GeneradorPdfPort generadorPdf,
            GeneradorExcelPort generadorExcel) {
        this.consultarHistorial = consultarHistorial;
        this.generadorPdf = generadorPdf;
        this.generadorExcel = generadorExcel;
    }

    @Operation(summary = "US 5.2.1 - Exportar historial de evaluaciones a PDF",
            description = "Operación exclusiva para ANALISTA_CREDITO. Recibe de 1 a 50 IDs de solicitantes "
                    + "positivos, únicos y no nulos. Un ID genera el historial individual; varios generan "
                    + "un único PDF agrupado por solicitante. Si algún solicitante no existe, no se genera "
                    + "un reporte parcial. Devuelve application/pdf como attachment con nombre "
                    + "historial-evaluaciones.pdf. Los errores usan el sobre ApiError. "
                    + "Ver docs/api/exportacion-historial.md para el contrato completo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento PDF generado",
                    content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida (VALIDATION_ERROR)",
                    content = @Content(schema = @Schema(implementation = com.crediticio.evaluationquery.shared.response.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Algún solicitante no existe (SOLICITANTE_NO_ENCONTRADO)",
                    content = @Content(schema = @Schema(implementation = com.crediticio.evaluationquery.shared.response.ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Error interno (INTERNAL_ERROR)",
                    content = @Content(schema = @Schema(implementation = com.crediticio.evaluationquery.shared.response.ApiError.class)))
    })
    @PostMapping(value = "/pdf", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/pdf")
    public ResponseEntity<byte[]> exportarPdf(@Valid @RequestBody SolicitudExportacionHistorial solicitud) {
        byte[] documento = generadorPdf.generar(consultarHistorial.consultar(solicitud.solicitanteIds()));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=historial-evaluaciones.pdf")
                .contentLength(documento.length)
                .body(documento);
    }

    @Operation(summary = "US 5.2.2 - Exportar historial de evaluaciones a Excel",
            description = "Operación exclusiva para ANALISTA_CREDITO. Recibe de 1 a 50 IDs de solicitantes "
                    + "positivos, únicos y no nulos. Un ID genera el historial individual; varios generan "
                    + "un único XLSX agrupado por solicitante. Si algún solicitante no existe, no se genera "
                    + "un reporte parcial. Devuelve el MIME type XLSX como attachment con nombre "
                    + "historial-evaluaciones.xlsx. Los errores usan el sobre ApiError. "
                    + "Ver docs/api/exportacion-historial.md para el contrato completo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento Excel generado",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida (VALIDATION_ERROR)",
                    content = @Content(schema = @Schema(implementation = com.crediticio.evaluationquery.shared.response.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Algún solicitante no existe (SOLICITANTE_NO_ENCONTRADO)",
                    content = @Content(schema = @Schema(implementation = com.crediticio.evaluationquery.shared.response.ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Error interno (INTERNAL_ERROR)",
                    content = @Content(schema = @Schema(implementation = com.crediticio.evaluationquery.shared.response.ApiError.class)))
    })
    @PostMapping(value = "/excel", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportarExcel(@Valid @RequestBody SolicitudExportacionHistorial solicitud) {
        byte[] documento = generadorExcel.generar(consultarHistorial.consultar(solicitud.solicitanteIds()));
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=historial-evaluaciones.xlsx")
                .contentLength(documento.length)
                .body(documento);
    }
}
