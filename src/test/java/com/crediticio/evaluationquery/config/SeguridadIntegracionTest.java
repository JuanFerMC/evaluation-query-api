package com.crediticio.evaluationquery.config;

import com.crediticio.evaluationquery.support.ClavesRsaPrueba;
import com.crediticio.evaluationquery.support.TokensPrueba;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU10 en evaluation-query-api: cadena de filtros real y tokens RS256 firmados con una clave
 * efímera. Aún no existen endpoints de negocio (HU13–HU15), por lo que cualquier ruta de la API
 * debe exigir un token válido (401) y, con token válido, denegarse (403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadIntegracionTest {

    private static final String EMISOR = "auth-api-prueba";
    private static final String RUTA_SIN_ENDPOINT = "/api/v1/ruta-sin-endpoint";
    private static final KeyPair CLAVES = ClavesRsaPrueba.generar();
    private static final KeyPair OTRAS_CLAVES = ClavesRsaPrueba.generar();

    @TempDir
    static Path directorioClaves;

    @DynamicPropertySource
    static void configurarClavePublica(DynamicPropertyRegistry registro) {
        registro.add("evaluationquery.jwt.clave-publica",
                () -> "file:" + ClavesRsaPrueba.escribirClavePublica(CLAVES, directorioClaves));
    }

    @Autowired
    private MockMvc mockMvc;

    private static String bearer(String rol) {
        return "Bearer " + TokensPrueba.firmar(CLAVES, TokensPrueba.claimsValidos(EMISOR, rol).build());
    }

    private static void traceIdDelCuerpoIgualAlDeLaCabecera(MvcResult resultado) throws Exception {
        String cabecera = resultado.getResponse().getHeader("X-Trace-Id");
        assertThat(cabecera).isNotBlank();
        assertThat(resultado.getResponse().getContentAsString()).contains("\"traceId\":\"" + cabecera + "\"");
    }

    @Test
    void sinTokenDebeResponder401ConApiErrorYTraceId() throws Exception {
        MvcResult resultado = mockMvc.perform(get(RUTA_SIN_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"))
                .andReturn();

        traceIdDelCuerpoIgualAlDeLaCabecera(resultado);
    }

    @Test
    void conUnTokenMalFormadoDebeResponder401() throws Exception {
        mockMvc.perform(get(RUTA_SIN_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"));
    }

    @Test
    void conUnTokenExpiradoDebeResponder401() throws Exception {
        String expirado = TokensPrueba.firmar(CLAVES, TokensPrueba.claimsValidos(EMISOR, JwtConfig.ROL_ANALISTA)
                .issueTime(Date.from(Instant.now().minus(Duration.ofMinutes(20))))
                .expirationTime(Date.from(Instant.now().minus(Duration.ofMinutes(5))))
                .build());

        mockMvc.perform(get(RUTA_SIN_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + expirado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conUnTokenFirmadoPorOtraClaveDebeResponder401() throws Exception {
        String ajeno = TokensPrueba.firmar(OTRAS_CLAVES,
                TokensPrueba.claimsValidos(EMISOR, JwtConfig.ROL_ANALISTA).build());

        mockMvc.perform(get(RUTA_SIN_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {JwtConfig.ROL_ADMINISTRADOR, JwtConfig.ROL_ANALISTA, "AUDITOR"})
    void conTokenValidoUnaRutaSinEndpointDebeDenegarseCon403(String rol) throws Exception {
        MvcResult resultado = mockMvc.perform(get(RUTA_SIN_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"))
                .andReturn();

        traceIdDelCuerpoIgualAlDeLaCabecera(resultado);
    }

    /** HU14: con la saga deshabilitada (por defecto) la operación interna no existe y ningún token la autoriza. */
    @Test
    void conLaSagaDeshabilitadaLaGeneracionInternaDebeDenegarse() throws Exception {
        mockMvc.perform(put("/internal/v1/evaluaciones/1/recomendaciones"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/internal/v1/evaluaciones/1/recomendaciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(JwtConfig.ROL_ADMINISTRADOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));
    }

    @Test
    void healthDebeSerPublicoYSinDetalles() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void otrosEndpointsDeActuatorNoDebenSerAccesibles() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, bearer(JwtConfig.ROL_ADMINISTRADOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noDebeCrearseSesionHttp() throws Exception {
        MvcResult resultado = mockMvc.perform(get(RUTA_SIN_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(JwtConfig.ROL_ANALISTA)))
                .andReturn();

        assertThat(resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(resultado.getRequest().getSession(false)).isNull();
    }

    @Test
    void unaCookieSinBearerNoDebeAutenticarLaPeticion() throws Exception {
        mockMvc.perform(get(RUTA_SIN_ENDPOINT)
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "jwt-falso")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"));
    }

    @Test
    void elPreflightNoDebePermitirCredencialesAutomaticas() throws Exception {
        mockMvc.perform(options(RUTA_SIN_ENDPOINT)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void elPreflightDesdeUnOrigenPermitidoDebeAdmitirAuthorization() throws Exception {
        mockMvc.perform(options(RUTA_SIN_ENDPOINT)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                        .containsIgnoringCase("Authorization"));
    }

    @Test
    void elPreflightDesdeUnOrigenNoPermitidoDebeRechazarse() throws Exception {
        mockMvc.perform(options(RUTA_SIN_ENDPOINT)
                        .header(HttpHeaders.ORIGIN, "https://sitio-no-permitido.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lasRespuestasDebenExponerXTraceIdAlNavegador() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(result -> assertThat(List.of(
                        result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS).split(",\\s*")))
                        .contains("X-Trace-Id"));
    }
}
