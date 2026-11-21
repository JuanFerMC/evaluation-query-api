package com.crediticio.evaluationquery.historial.infrastructure.input;

import com.crediticio.evaluationquery.config.JwtConfig;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import com.crediticio.evaluationquery.historial.domain.SolicitanteNoEncontradoException;
import com.crediticio.evaluationquery.historial.ports.input.ConsultarHistorialExportacionUseCase;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorExcelPort;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorPdfPort;
import com.crediticio.evaluationquery.support.ClavesRsaPrueba;
import com.crediticio.evaluationquery.support.TokensPrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportacionHistorialControllerIT {

    private static final String EMISOR = "auth-api-prueba";
    private static final KeyPair CLAVES = ClavesRsaPrueba.generar();

    @org.junit.jupiter.api.io.TempDir
    static Path directorioClaves;

    @DynamicPropertySource
    static void configurarClavePublica(DynamicPropertyRegistry registro) {
        registro.add("evaluationquery.jwt.clave-publica",
                () -> "file:" + ClavesRsaPrueba.escribirClavePublica(CLAVES, directorioClaves));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarHistorialExportacionUseCase consulta;

    @MockitoBean
    private GeneradorPdfPort generadorPdf;

    @MockitoBean
    private GeneradorExcelPort generadorExcel;

    private String tokenAnalista;
    private String tokenAdministrador;

    @BeforeEach
    void configurarTokens() {
        tokenAnalista = token(JwtConfig.ROL_ANALISTA);
        tokenAdministrador = token(JwtConfig.ROL_ADMINISTRADOR);
        clearInvocations(consulta, generadorPdf, generadorExcel);
    }

    private static String token(String rol) {
        return "Bearer " + TokensPrueba.firmar(CLAVES, TokensPrueba.claimsValidos(EMISOR, rol).build());
    }

    @Test
    void exportacionPdfDebeResponderArchivoBinarioConEncabezadosCorrectos() throws Exception {
        byte[] pdf = "%PDF-1.7 contenido de prueba".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        List<HistorialSolicitanteExportacion> historiales =
                List.of(new HistorialSolicitanteExportacion(101L, "Solicitante 101L", List.of()));
        when(consulta.consultar(List.of(101L))).thenReturn(historiales);
        when(generadorPdf.generar(historiales)).thenReturn(pdf);

        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, tokenAnalista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_PDF)
                        .content("{\"solicitanteIds\":[101]}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=historial-evaluaciones.pdf"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, pdf.length))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).containsExactly(pdf));

        verify(consulta).consultar(List.of(101L));
        verify(generadorPdf).generar(historiales);
        verify(generadorExcel, never()).generar(anyList());
    }

    @Test
    void exportacionExcelDebeResponderArchivoBinarioConEncabezadosCorrectos() throws Exception {
        byte[] xlsx = new byte[]{'P', 'K', 3, 4, 10, 20, 30};
        List<HistorialSolicitanteExportacion> historiales =
                List.of(new HistorialSolicitanteExportacion(202L, "Solicitante 202L", List.of()));
        when(consulta.consultar(List.of(202L))).thenReturn(historiales);
        when(generadorExcel.generar(historiales)).thenReturn(xlsx);

        mockMvc.perform(post("/api/v1/evaluaciones/export/excel")
                        .header(HttpHeaders.AUTHORIZATION, tokenAnalista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .content("{\"solicitanteIds\":[202]}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=historial-evaluaciones.xlsx"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, xlsx.length))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).containsExactly(xlsx));

        verify(consulta).consultar(List.of(202L));
        verify(generadorExcel).generar(historiales);
        verify(generadorPdf, never()).generar(anyList());
    }

    @Test
    void administradorDebeRecibir403EnExportacionSinInvocarGeneradores() throws Exception {
        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[101]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));

        verify(consulta, never()).consultar(anyList());
        verify(generadorPdf, never()).generar(anyList());
    }

    @Test
    void jsonVacioDebeResponder400SinConsultarNiGenerar() throws Exception {
        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, tokenAnalista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(consulta, never()).consultar(anyList());
        verify(generadorPdf, never()).generar(anyList());
    }

    @Test
    void identificadoresNulosONoPositivosDebenResponder400() throws Exception {
        for (String cuerpo : List.of(
                "{\"solicitanteIds\":[null]}",
                "{\"solicitanteIds\":[0]}",
                "{\"solicitanteIds\":[-5]}")) {
            mockMvc.perform(post("/api/v1/evaluaciones/export/excel")
                            .header(HttpHeaders.AUTHORIZATION, tokenAnalista)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }
        verify(consulta, never()).consultar(anyList());
        verify(generadorExcel, never()).generar(anyList());
    }

    @Test
    void masDeCincuentaSolicitantesDebeResponder400() throws Exception {
        String ids = java.util.stream.LongStream.rangeClosed(1, 51)
                .mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, tokenAnalista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(consulta, never()).consultar(anyList());
        verify(generadorPdf, never()).generar(anyList());
    }

    @Test
    void jsonMalformadoDebeResponder400() throws Exception {
        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, tokenAnalista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(consulta, never()).consultar(anyList());
        verify(generadorPdf, never()).generar(anyList());
    }

    @Test
    void solicitanteInexistenteDebeResponder404YSinGenerarArchivo() throws Exception {
        when(consulta.consultar(List.of(999L))).thenThrow(new SolicitanteNoEncontradoException());

        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, tokenAnalista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[999]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SOLICITANTE_NO_ENCONTRADO"));

        verify(generadorPdf, never()).generar(anyList());
    }

    @Test
    void sinTokenDebeResponder401YConRolNoAutorizado403() throws Exception {
        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[101]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"));

        mockMvc.perform(post("/api/v1/evaluaciones/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, token("AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitanteIds\":[101]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));

        verify(consulta, never()).consultar(anyList());
        verify(generadorPdf, never()).generar(anyList());
    }

    @Test
    void preflightPostDesdeOrigenPermitidoDebeAdmitirAuthorization() throws Exception {
        mockMvc.perform(options("/api/v1/evaluaciones/export/excel")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST"));
    }
}
