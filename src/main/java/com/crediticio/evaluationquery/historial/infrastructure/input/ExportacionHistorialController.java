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

    @Operation(summary = "Exportar historiales de evaluación a PDF",
            description = "Genera un PDF descargable con los historiales completos de hasta 50 solicitantes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento PDF generado",
                    content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "404", description = "Algún solicitante no existe"),
            @ApiResponse(responseCode = "500", description = "Error interno")
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

    @Operation(summary = "Exportar historiales de evaluación a Excel",
            description = "Genera un XLSX descargable con los historiales completos de hasta 50 solicitantes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento Excel generado",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "404", description = "Algún solicitante no existe"),
            @ApiResponse(responseCode = "500", description = "Error interno")
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
