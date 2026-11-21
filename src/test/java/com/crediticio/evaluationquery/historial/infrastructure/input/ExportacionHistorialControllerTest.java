package com.crediticio.evaluationquery.historial.infrastructure.input;

import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.SolicitudExportacionHistorial;
import com.crediticio.evaluationquery.historial.ports.input.ConsultarHistorialExportacionUseCase;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorExcelPort;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorPdfPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import com.crediticio.evaluationquery.shared.exception.GlobalExceptionHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ExportacionHistorialControllerTest {

    private final ConsultarHistorialExportacionUseCase consulta = mock(ConsultarHistorialExportacionUseCase.class);
    private final GeneradorPdfPort generadorPdf = mock(GeneradorPdfPort.class);
    private final GeneradorExcelPort generadorExcel = mock(GeneradorExcelPort.class);
    private final ExportacionHistorialController controller =
            new ExportacionHistorialController(consulta, generadorPdf, generadorExcel);
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void configurarMockMvc() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void debeRetornarPdfComoArchivoAdjunto() {
        var solicitud = new SolicitudExportacionHistorial(List.of(101L, 202L));
        var historiales = List.of(new HistorialSolicitanteExportacion(101L, "Solicitante 101", List.of()));
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

    @Test
    void debeRechazarUnaListaVaciaAntesDeConsultar() throws Exception {
        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verify(consulta, never()).consultar(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void debeRechazarMasDeCincuentaSolicitantes() throws Exception {
        String ids = java.util.stream.LongStream.rangeClosed(1, 51)
                .mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/api/v1/evaluaciones/export/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verify(consulta, never()).consultar(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void debeRechazarIdentificadoresNulos() throws Exception {
        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[101,null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verify(consulta, never()).consultar(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void debeRechazarIdentificadoresNoPositivos() throws Exception {
        mockMvc.perform(post("/api/v1/evaluaciones/export/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[0]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verify(consulta, never()).consultar(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void debeRechazarUnCuerpoJsonMalformado() throws Exception {
        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
        verify(consulta, never()).consultar(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void debeConvertirUnaIllegalArgumentExceptionEnRespuesta400() throws Exception {
        when(consulta.consultar(List.of(101L)))
                .thenThrow(new IllegalArgumentException("IDs inválidos"));
        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[101]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("IDs inválidos"));
    }

}
