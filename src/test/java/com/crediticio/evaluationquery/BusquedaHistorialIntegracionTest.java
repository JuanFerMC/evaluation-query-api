package com.crediticio.evaluationquery;

import com.crediticio.evaluationquery.config.JwtConfig;
import com.crediticio.evaluationquery.support.ClavesRsaPrueba;
import com.crediticio.evaluationquery.support.DatosScoringPrueba;
import com.crediticio.evaluationquery.support.TokensPrueba;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Path;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US 5.1.2 de extremo a extremo: GET /api/v1/evaluaciones con la cadena de seguridad real, el controlador, el
 * servicio y el adaptador JDBC sobre H2. La compatibilidad de la consulta con PostgreSQL la comprueba
 * EsquemaScoringContratoIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusquedaHistorialIntegracionTest {

    private static final String RUTA = "/api/v1/evaluaciones";
    private static final String EMISOR = "auth-api-prueba";
    private static final KeyPair CLAVES = ClavesRsaPrueba.generar();
    private static final String NOMBRE_A = "Solicitante Filtro A";
    private static final String DOCUMENTO_A = "900000301";
    private static final String DOCUMENTO_B = "900000302";
    private static final String DOCUMENTO_C = "900000303";

    @TempDir
    static Path directorioClaves;

    @DynamicPropertySource
    static void configurarClavePublica(DynamicPropertyRegistry registro) {
        registro.add("evaluationquery.jwt.clave-publica",
                () -> "file:" + ClavesRsaPrueba.escribirClavePublica(CLAVES, directorioClaves));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long idSolicitanteA;
    /** ALTO de A, un segundo antes del 01/11/2026. */
    private long antesDelRango;
    /** ALTO de A, primer instante del 01/11/2026. */
    private long primerDia;
    /** ALTO de A, último segundo del 30/11/2026. */
    private long ultimoDia;
    /** ALTO de C, primer instante del 01/12/2026. */
    private long despuesDelRango;
    /** MEDIO y ALTO de B, misma fecha (15/11/2026): desempate por id descendente. */
    private long medioMismaFecha;
    private long altoMismaFecha;
    /** BAJO de A, 20/11/2026. */
    private long bajo;
    /** C, 05/11/2026, anterior a HU12 (sin categoría; puntaje derivado de sus detalles). */
    private long sinClasificar;
    /** ALTO de A, 10/11/2026, PENDIENTE: nunca aparece (HU15 CA08). */
    private long pendiente;

    @BeforeEach
    void prepararDatos() {
        DatosScoringPrueba datos = new DatosScoringPrueba(jdbcTemplate);
        datos.vaciar();
        idSolicitanteA = datos.solicitante(NOMBRE_A, DOCUMENTO_A);
        long idSolicitanteB = datos.solicitante("Solicitante Filtro B", DOCUMENTO_B);
        long idSolicitanteC = datos.solicitante("Solicitante Filtro C", DOCUMENTO_C);
        datos.solicitante("Sin Evaluaciones", "900000304");
        long politica = datos.politica(1);
        long regla = datos.regla(datos.riesgo("INGRESOS_MENSUALES"), ">=", "3000000", 20);

        antesDelRango = completada(datos, idSolicitanteA, LocalDateTime.of(2026, 10, 31, 23, 59, 59), "ALTO", politica);
        primerDia = completada(datos, idSolicitanteA, LocalDateTime.of(2026, 11, 1, 0, 0), "ALTO", politica);
        medioMismaFecha = completada(datos, idSolicitanteB, LocalDateTime.of(2026, 11, 15, 10, 0), "MEDIO", politica);
        altoMismaFecha = completada(datos, idSolicitanteB, LocalDateTime.of(2026, 11, 15, 10, 0), "ALTO", politica);
        ultimoDia = completada(datos, idSolicitanteA, LocalDateTime.of(2026, 11, 30, 23, 59, 59), "ALTO", politica);
        despuesDelRango = completada(datos, idSolicitanteC, LocalDateTime.of(2026, 12, 1, 0, 0), "ALTO", politica);
        bajo = completada(datos, idSolicitanteA, LocalDateTime.of(2026, 11, 20, 9, 0), "BAJO", politica);
        pendiente = datos.evaluacionConEstado(idSolicitanteA, LocalDateTime.of(2026, 11, 10, 9, 0), 40, "ALTO",
                politica, "PENDIENTE");
        sinClasificar = datos.evaluacionAnteriorAHu12(idSolicitanteC, LocalDateTime.of(2026, 11, 5, 8, 0));
        datos.detalle(sinClasificar, regla, ">=", "3000000", 20, true, 20);
    }

    private static long completada(DatosScoringPrueba datos, long idSolicitante, LocalDateTime fecha,
            String categoria, long politica) {
        return datos.evaluacionConEstado(idSolicitante, fecha, 40, categoria, politica, "COMPLETADA");
    }

    private static String bearer(String rol) {
        return "Bearer " + TokensPrueba.firmar(CLAVES, TokensPrueba.claimsValidos(EMISOR, rol).build());
    }

    private static MockHttpServletRequestBuilder consulta() {
        return get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(JwtConfig.ROL_ANALISTA));
    }

    private List<Long> ids(MvcResult resultado) throws Exception {
        List<Number> ids = JsonPath.read(resultado.getResponse().getContentAsString(), "$.evaluaciones[*].idEvaluacion");
        return ids.stream().map(Number::longValue).toList();
    }

    private List<Long> idsDe(MockHttpServletRequestBuilder peticion, long totalEsperado) throws Exception {
        MvcResult resultado = mockMvc.perform(peticion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistros").value(totalEsperado))
                .andReturn();
        return ids(resultado);
    }

    // ---- Filtros ----

    @Test
    void sinFiltrosDebeDevolverTodoElHistorialSinPendientesOrdenadoYConElTotal() throws Exception {
        MvcResult resultado = mockMvc.perform(consulta())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalRegistros").value(8))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andReturn();

        assertThat(ids(resultado)).containsExactly(despuesDelRango, ultimoDia, bajo, altoMismaFecha,
                medioMismaFecha, sinClasificar, primerDia, antesDelRango);
        assertThat(ids(resultado)).doesNotContain(pendiente);
    }

    @Test
    void cadaElementoDebeTenerElFormatoDelHistorialSinDatosPersonales() throws Exception {
        MvcResult resultado = mockMvc.perform(consulta().param("numeroDocumento", DOCUMENTO_C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluaciones[0].idEvaluacion").value(despuesDelRango))
                .andExpect(jsonPath("$.evaluaciones[0].fechaEvaluacion").value("2026-12-01T00:00:00"))
                .andExpect(jsonPath("$.evaluaciones[0].categoria").value("ALTO"))
                .andExpect(jsonPath("$.evaluaciones[0].estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.evaluaciones[0].explicacion")
                        .value("/api/v1/evaluaciones/" + despuesDelRango + "/explicacion"))
                .andExpect(jsonPath("$.evaluaciones[1].clasificada").value(false))
                .andExpect(jsonPath("$.evaluaciones[1].scoreTotal").value(20))
                .andExpect(jsonPath("$.evaluaciones[1].origenPuntaje").value("DERIVADO_DE_DETALLES"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain(DOCUMENTO_C, NOMBRE_A, "idSolicitante", "numeroDocumento", "ingresos", "deudas");
    }

    @Test
    void fechaDesdeDebeIncluirElPrimerInstanteDelDia() throws Exception {
        assertThat(idsDe(consulta().param("fechaDesde", "2026-11-01"), 7))
                .containsExactly(despuesDelRango, ultimoDia, bajo, altoMismaFecha, medioMismaFecha, sinClasificar,
                        primerDia);
    }

    @Test
    void fechaHastaDebeIncluirElDiaCompleto() throws Exception {
        assertThat(idsDe(consulta().param("fechaHasta", "2026-11-30"), 7))
                .containsExactly(ultimoDia, bajo, altoMismaFecha, medioMismaFecha, sinClasificar, primerDia,
                        antesDelRango);
    }

    @Test
    void elRangoDebeIncluirElPrimerYElUltimoDiaYExcluirLosBordesExteriores() throws Exception {
        List<Long> ids = idsDe(consulta().param("fechaDesde", "2026-11-01").param("fechaHasta", "2026-11-30"), 6);

        assertThat(ids).containsExactly(ultimoDia, bajo, altoMismaFecha, medioMismaFecha, sinClasificar, primerDia);
        assertThat(ids).doesNotContain(antesDelRango, despuesDelRango);
    }

    @Test
    void unRangoDeUnSoloDiaDebeIncluirTodoEseDia() throws Exception {
        assertThat(idsDe(consulta().param("fechaDesde", "2026-11-15").param("fechaHasta", "2026-11-15"), 2))
                .containsExactly(altoMismaFecha, medioMismaFecha);
    }

    @Test
    void elDocumentoDebeFiltrarPorElNumeroDeDocumentoDelSolicitante() throws Exception {
        assertThat(idsDe(consulta().param("numeroDocumento", DOCUMENTO_A), 4))
                .containsExactly(ultimoDia, bajo, primerDia, antesDelRango);
        assertThat(idsDe(consulta().param("numeroDocumento", "  " + DOCUMENTO_A + " "), 4))
                .containsExactly(ultimoDia, bajo, primerDia, antesDelRango);
    }

    @Test
    void elIdentificadorInternoDelSolicitanteNoDebeTratarseComoDocumento() throws Exception {
        assertThat(idsDe(consulta().param("numeroDocumento", String.valueOf(idSolicitanteA)), 0)).isEmpty();
    }

    @Test
    void unDocumentoVacioNoDebeFiltrar() throws Exception {
        idsDe(consulta().param("numeroDocumento", "  "), 8);
    }

    @Test
    void elNivelDeRiesgoDebeFiltrarPorLaCategoriaPersistida() throws Exception {
        assertThat(idsDe(consulta().param("categoria", "ALTO"), 5))
                .containsExactly(despuesDelRango, ultimoDia, altoMismaFecha, primerDia, antesDelRango);
        assertThat(idsDe(consulta().param("categoria", "MEDIO"), 1)).containsExactly(medioMismaFecha);
        assertThat(idsDe(consulta().param("categoria", "BAJO"), 1)).containsExactly(bajo);
    }

    /** Escenario 1 de la US 5.1.2: del 01/11/2026 al 30/11/2026 con nivel de riesgo Alto. */
    @Test
    void elRangoDeFechasYElNivelDeRiesgoDebenCumplirseAmbos() throws Exception {
        assertThat(idsDe(consulta().param("fechaDesde", "2026-11-01").param("fechaHasta", "2026-11-30")
                .param("categoria", "ALTO"), 3))
                .containsExactly(ultimoDia, altoMismaFecha, primerDia);
    }

    @Test
    void todosLosFiltrosCombinadosDebenAplicarseConAnd() throws Exception {
        assertThat(idsDe(consulta().param("fechaDesde", "2026-11-01").param("fechaHasta", "2026-11-30")
                .param("numeroDocumento", DOCUMENTO_A).param("categoria", "ALTO"), 2))
                .containsExactly(ultimoDia, primerDia);
        assertThat(idsDe(consulta().param("fechaDesde", "2026-11-01").param("fechaHasta", "2026-11-30")
                .param("numeroDocumento", DOCUMENTO_B).param("categoria", "BAJO"), 0)).isEmpty();
    }

    // ---- Paginación y total ----

    @Test
    void laPaginacionDebeRecorrerTodasLasCoincidenciasSinRepetirNiOmitir() throws Exception {
        List<Long> recorridos = new ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            MvcResult resultado = mockMvc.perform(consulta().param("pagina", String.valueOf(pagina)).param("tamano", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagina").value(pagina))
                    .andExpect(jsonPath("$.tamano").value(3))
                    .andExpect(jsonPath("$.totalRegistros").value(8))
                    .andExpect(jsonPath("$.totalPaginas").value(3))
                    .andExpect(jsonPath("$.evaluaciones.length()").value(pagina < 2 ? 3 : 2))
                    .andReturn();
            recorridos.addAll(ids(resultado));
        }

        assertThat(recorridos).containsExactly(despuesDelRango, ultimoDia, bajo, altoMismaFecha, medioMismaFecha,
                sinClasificar, primerDia, antesDelRango);
    }

    @Test
    void elTotalDebeContarTodasLasCoincidenciasNoSoloLasDeLaPagina() throws Exception {
        mockMvc.perform(consulta().param("categoria", "ALTO").param("tamano", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluaciones.length()").value(2))
                .andExpect(jsonPath("$.totalRegistros").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3));
    }

    @Test
    void unaPaginaPosteriorALaUltimaDebeVenirVaciaConElTotalReal() throws Exception {
        mockMvc.perform(consulta().param("categoria", "ALTO").param("pagina", "9").param("tamano", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluaciones.length()").value(0))
                .andExpect(jsonPath("$.totalRegistros").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3));
    }

    // ---- Sin resultados (escenario 2) ----

    @Test
    void unaBusquedaSinCoincidenciasDebeResponder200ConListaVaciaYTotalCero() throws Exception {
        mockMvc.perform(consulta().param("fechaDesde", "2025-01-01").param("fechaHasta", "2025-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluaciones").isArray())
                .andExpect(jsonPath("$.evaluaciones.length()").value(0))
                .andExpect(jsonPath("$.totalRegistros").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0))
                .andExpect(jsonPath("$.pagina").value(0));
    }

    @Test
    void unDocumentoInexistenteDebeResponder200SinResultados() throws Exception {
        assertThat(idsDe(consulta().param("numeroDocumento", "000000000"), 0)).isEmpty();
    }

    // ---- Validación ----

    @Test
    void unRangoInvertidoDebeResponder400ConTraceId() throws Exception {
        String traceId = UUID.randomUUID().toString();
        mockMvc.perform(consulta().param("fechaDesde", "2026-11-30").param("fechaHasta", "2026-11-01")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", traceId))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El parámetro 'fechaDesde' no puede ser posterior a 'fechaHasta'"))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }

    @ParameterizedTest
    @CsvSource({
            "fechaDesde, 01/11/2026",
            "fechaHasta, 2026-13-01",
            "categoria, Alto",
            "categoria, CRITICO",
            "pagina, -1",
            "pagina, uno",
            "tamano, 0",
            "tamano, 101"
    })
    void unParametroInvalidoDebeResponder400(String parametro, String valor) throws Exception {
        mockMvc.perform(consulta().param(parametro, valor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ---- Seguridad y solo lectura ----

    @ParameterizedTest
    @ValueSource(strings = {JwtConfig.ROL_ADMINISTRADOR, JwtConfig.ROL_ANALISTA})
    void losRolesDelModeloPuedenFiltrarElHistorial(String rol) throws Exception {
        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                .andExpect(status().isOk());
    }

    @Test
    void elFiltroExigeUnTokenYUnRolDelModelo() throws Exception {
        mockMvc.perform(get(RUTA).param("categoria", "ALTO"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"));
        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));
    }

    @Test
    void laBusquedaNoDebeModificarDatos() throws Exception {
        String antes = huella();

        mockMvc.perform(consulta().param("fechaDesde", "2026-11-01").param("categoria", "ALTO"))
                .andExpect(status().isOk());

        assertThat(huella()).isEqualTo(antes);
    }

    private String huella() {
        return jdbcTemplate.queryForObject("SELECT CONCAT((SELECT COUNT(*) FROM evaluacion), '|', "
                + "(SELECT COUNT(*) FROM detalle_evaluacion), '|', "
                + "(SELECT COALESCE(SUM(score_total), 0) FROM evaluacion))", String.class);
    }
}
