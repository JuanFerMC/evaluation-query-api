package com.crediticio.evaluationquery.historial.infrastructure.input;

import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.SolicitudExportacionHistorial;
import com.crediticio.evaluationquery.historial.ports.input.ConsultarHistorialExportacionUseCase;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorExcelPort;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorPdfPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportacionHistorialControllerTest {

    private final ConsultarHistorialExportacionUseCase consulta = mock(ConsultarHistorialExportacionUseCase.class);
    private final GeneradorPdfPort generadorPdf = mock(GeneradorPdfPort.class);
    private final GeneradorExcelPort generadorExcel = mock(GeneradorExcelPort.class);
    private final ExportacionHistorialController controller =
            new ExportacionHistorialController(consulta, generadorPdf, generadorExcel);

    @Test
    void debeRetornarPdfComoArchivoAdjunto() {
        var solicitud = new SolicitudExportacionHistorial(List.of(101L, 202L));
        var historiales = List.of(new HistorialSolicitanteExportacion(101L, List.of()));
        byte[] archivo = "%PDF-test".getBytes();

        when(consulta.consultar(solicitud.solicitanteIds())).thenReturn(historiales);
        when(generadorPdf.generar(historiales)).thenReturn(archivo);

        var respuesta = controller.exportarPdf(solicitud);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(respuesta.getHeaders().getFirst("Content-Disposition"))
                .isEqualTo("attachment; filename=historial-evaluaciones.pdf");
        assertThat(respuesta.getHeaders().getContentLength()).isEqualTo(archivo.length);
        assertThat(respuesta.getBody()).containsExactly(archivo);
        verify(consulta).consultar(solicitud.solicitanteIds());
        verify(generadorPdf).generar(historiales);
    }

    @Test
    void debeRetornarExcelComoArchivoAdjunto() {
        var solicitud = new SolicitudExportacionHistorial(List.of(101L));
        var historiales = List.of(new HistorialSolicitanteExportacion(101L, List.of()));
        byte[] archivo = new byte[]{'P', 'K', 3, 4};

        when(consulta.consultar(solicitud.solicitanteIds())).thenReturn(historiales);
        when(generadorExcel.generar(historiales)).thenReturn(archivo);

        var respuesta = controller.exportarExcel(solicitud);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(respuesta.getHeaders().getFirst("Content-Disposition"))
                .isEqualTo("attachment; filename=historial-evaluaciones.xlsx");
        assertThat(respuesta.getHeaders().getContentLength()).isEqualTo(archivo.length);
        assertThat(respuesta.getBody()).containsExactly(archivo);
        verify(consulta).consultar(solicitud.solicitanteIds());
        verify(generadorExcel).generar(historiales);
    }
}
