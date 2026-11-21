package com.crediticio.evaluationquery;

import com.crediticio.evaluationquery.config.JwtConfig;
import com.crediticio.evaluationquery.reportes.ports.output.DistribucionRiesgoPort;
import com.crediticio.evaluationquery.support.ClavesRsaPrueba;
import com.crediticio.evaluationquery.support.TokensPrueba;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.security.KeyPair;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US 7.1.1: si la base no responde, el reporte devuelve 500 INTERNAL_ERROR, nunca una distribución en cero. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DistribucionRiesgoErrorConsultaIntegracionTest {

    private static final KeyPair CLAVES = ClavesRsaPrueba.generar();

    @TempDir
    static Path directorioClaves;

    @DynamicPropertySource
    static void configurarClavePublica(DynamicPropertyRegistry registro) {
        registro.add("evaluationquery.jwt.clave-publica",
                () -> "file:" + ClavesRsaPrueba.escribirClavePublica(CLAVES, directorioClaves));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DistribucionRiesgoPort distribucionRiesgoPort;

    @Test
    void unFalloDeLaBaseDebeResponder500ConElFormatoComun() throws Exception {
        given(distribucionRiesgoPort.contarPorCategoria())
                .willThrow(new DataAccessResourceFailureException("conexión rechazada"));

        mockMvc.perform(get("/api/v1/reportes/distribucion-riesgo").header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + TokensPrueba.firmar(CLAVES, TokensPrueba.claimsValidos("auth-api-prueba",
                                JwtConfig.ROL_ANALISTA).build())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Ocurrió un error interno. Intente nuevamente más tarde"))
                .andExpect(jsonPath("$.totalEvaluaciones").doesNotExist());
    }
}
