package com.crediticio.evaluationquery;

import com.crediticio.evaluationquery.config.JwtConfig;
import com.crediticio.evaluationquery.support.ClavesRsaPrueba;
import com.crediticio.evaluationquery.support.DatosScoringPrueba;
import com.crediticio.evaluationquery.support.TokensPrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Path;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US 7.1.1 de extremo a extremo: GET /api/v1/reportes/distribucion-riesgo con la cadena de seguridad real, el
 * controlador, el servicio y el adaptador JDBC sobre H2. La unidad de conteo es la evaluación.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DistribucionRiesgoIntegracionTest {

    private static final String RUTA = "/api/v1/reportes/distribucion-riesgo";
    private static final String EMISOR = "auth-api-prueba";
    private static final KeyPair CLAVES = ClavesRsaPrueba.generar();
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 10, 9, 0);

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

    private DatosScoringPrueba datos;
    private long solicitanteA;
    private long politica;

    /**
     * A tiene tres evaluaciones (BAJO y ALTO COMPLETADA, MEDIO PENDIENTE) con varios detalles cada una; B tiene una
     * BAJO anterior a la saga (estado null) y una ALTO; C solo una anterior a HU12, sin categoría; D ninguna.
     * Resultado esperado: BAJO 2, MEDIO 1, ALTO 2, total 5 (no 3 solicitantes), 1 sin clasificar.
     */
    @BeforeEach
    void prepararDatos() {
        datos = new DatosScoringPrueba(jdbcTemplate);
        datos.vaciar();
        solicitanteA = datos.solicitante("Solicitante A", "900000401");
        long solicitanteB = datos.solicitante("Solicitante B", "900000402");
        long solicitanteC = datos.solicitante("Solicitante C", "900000403");
        datos.solicitante("Solicitante D", "900000404");
        politica = datos.politica(1);
        long reglaMoras = datos.regla(datos.riesgo("NUMERO_MORAS"), ">", "2", -20);
        long reglaIngresos = datos.regla(datos.riesgo("INGRESOS_MENSUALES"), ">=", "3000000", 20);

        long[] evaluacionesDeA = {
                datos.evaluacionConEstado(solicitanteA, FECHA, -40, "BAJO", politica, "COMPLETADA"),
                datos.evaluacionConEstado(solicitanteA, FECHA.plusDays(1), 40, "ALTO", politica, "COMPLETADA"),
                datos.evaluacionConEstado(solicitanteA, FECHA.plusDays(2), 0, "MEDIO", politica, "PENDIENTE")};
        for (long id : evaluacionesDeA) {
            datos.detalle(id, reglaMoras, ">", "2", -20, true, -20);
            datos.detalle(id, reglaIngresos, ">=", "3000000", 20, true, 20);
        }
        datos.evaluacionClasificada(solicitanteB, FECHA.minusDays(5), -35, "BAJO", politica);
        datos.evaluacionConEstado(solicitanteB, FECHA.plusDays(3), 50, "ALTO", politica, "COMPLETADA");
        long sinClasificar = datos.evaluacionAnteriorAHu12(solicitanteC, FECHA.minusDays(30));
        datos.detalle(sinClasificar, reglaIngresos, ">=", "3000000", 20, true, 20);
    }

    private static String bearer(String rol) {
        return "Bearer " + TokensPrueba.firmar(CLAVES, TokensPrueba.claimsValidos(EMISOR, rol).build());
    }

    private ResultActions consultar() throws Exception {
        return mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(JwtConfig.ROL_ANALISTA)))
                .andExpect(status().isOk());
    }

    private static void esperarNivel(ResultActions resultado, int indice, String categoria, long cantidad,
            double porcentaje) throws Exception {
        resultado.andExpect(jsonPath("$.distribucion[%d].categoria".formatted(indice)).value(categoria))
                .andExpect(jsonPath("$.distribucion[%d].cantidad".formatted(indice)).value(cantidad))
                .andExpect(jsonPath("$.distribucion[%d].porcentaje".formatted(indice)).value(porcentaje));
    }

    // ---- Cantidades, total y porcentajes ----

    @Test
    void conLosTresNivelesDebeContarCadaEvaluacionUnaVezConTotalYPorcentajes() throws Exception {
        ResultActions resultado = consultar()
                .andExpect(jsonPath("$.totalEvaluaciones").value(5))
                .andExpect(jsonPath("$.evaluacionesSinClasificar").value(1))
                .andExpect(jsonPath("$.distribucion.length()").value(3));
        esperarNivel(resultado, 0, "BAJO", 2, 40.0);
        esperarNivel(resultado, 1, "MEDIO", 1, 20.0);
        esperarNivel(resultado, 2, "ALTO", 2, 40.0);
    }

    @Test
    void elTotalDebeCoincidirConLasEvaluacionesClasificadasPersistidasSinDobleConteoPorDetalles() throws Exception {
        long clasificadasEnBase = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evaluacion WHERE categoria IS NOT NULL", Long.class);
        long detalles = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM detalle_evaluacion", Long.class);
        assertThat(detalles).isGreaterThan(clasificadasEnBase); // un join con detalles inflaría el conteo

        consultar().andExpect(jsonPath("$.totalEvaluaciones").value(clasificadasEnBase));
    }

    @Test
    void unSolicitanteConVariasEvaluacionesAportaUnaPorCadaEvaluacion() throws Exception {
        long solicitantesClasificados = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT id_solicitante) FROM evaluacion WHERE categoria IS NOT NULL", Long.class);
        assertThat(solicitantesClasificados).isEqualTo(2);

        // A (3 evaluaciones) cuenta 3 y B (2) cuenta 2: el total es de evaluaciones, no de solicitantes
        consultar().andExpect(jsonPath("$.totalEvaluaciones").value(5));
    }

    @Test
    void sinEvaluacionesDebeResponder200ConTodoEnCero() throws Exception {
        datos.vaciar();

        ResultActions resultado = consultar()
                .andExpect(jsonPath("$.totalEvaluaciones").value(0))
                .andExpect(jsonPath("$.evaluacionesSinClasificar").value(0))
                .andExpect(jsonPath("$.distribucion.length()").value(3));
        esperarNivel(resultado, 0, "BAJO", 0, 0.0);
        esperarNivel(resultado, 1, "MEDIO", 0, 0.0);
        esperarNivel(resultado, 2, "ALTO", 0, 0.0);
    }

    @Test
    void conUnaSolaCategoriaEstaDebeSerElCienPorCiento() throws Exception {
        datos.vaciar();
        long solicitante = datos.solicitante("Unico", "900000499");
        long otraPolitica = datos.politica(2);
        datos.evaluacionConEstado(solicitante, FECHA, 10, "MEDIO", otraPolitica, "COMPLETADA");
        datos.evaluacionConEstado(solicitante, FECHA.plusDays(1), 12, "MEDIO", otraPolitica, "COMPLETADA");

        ResultActions resultado = consultar().andExpect(jsonPath("$.totalEvaluaciones").value(2));
        esperarNivel(resultado, 0, "BAJO", 0, 0.0);
        esperarNivel(resultado, 1, "MEDIO", 2, 100.0);
        esperarNivel(resultado, 2, "ALTO", 0, 0.0);
    }

    /** Escenario 2: una evaluación registrada después se refleja en la consulta siguiente. */
    @Test
    void unaNuevaEvaluacionDebeReflejarseEnLaConsultaSiguiente() throws Exception {
        consultar().andExpect(jsonPath("$.totalEvaluaciones").value(5));

        datos.evaluacionConEstado(solicitanteA, FECHA.plusDays(10), 60, "ALTO", politica, "PENDIENTE");

        ResultActions resultado = consultar().andExpect(jsonPath("$.totalEvaluaciones").value(6));
        esperarNivel(resultado, 0, "BAJO", 2, 33.33);
        esperarNivel(resultado, 1, "MEDIO", 1, 16.67);
        esperarNivel(resultado, 2, "ALTO", 3, 50.0);
    }

    // ---- Errores de datos ----

    @Test
    void unaCategoriaPersistidaNoReconocidaDebeResponder500SinReinterpretarse() throws Exception {
        datos.evaluacionConEstado(solicitanteA, FECHA, 0, "CRITICO", politica, "COMPLETADA");
        String traceId = UUID.randomUUID().toString();

        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(JwtConfig.ROL_ANALISTA))
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Trace-Id", traceId))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }

    // ---- Seguridad y solo lectura ----

    @ParameterizedTest
    @ValueSource(strings = {JwtConfig.ROL_ADMINISTRADOR, JwtConfig.ROL_ANALISTA})
    void losRolesDirectivoYAnalistaDebenVerLaMismaDistribucion(String rol) throws Exception {
        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvaluaciones").value(5))
                .andExpect(jsonPath("$.distribucion[2].cantidad").value(2));
    }

    @Test
    void sinTokenDebeResponder401YConOtroRol403() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"));
        mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.totalEvaluaciones").doesNotExist());
    }

    @Test
    void elReporteNoDebeModificarDatos() throws Exception {
        String antes = huella();

        consultar();

        assertThat(huella()).isEqualTo(antes);
    }

    private String huella() {
        return jdbcTemplate.queryForObject("SELECT CONCAT((SELECT COUNT(*) FROM evaluacion), '|', "
                + "(SELECT COUNT(*) FROM detalle_evaluacion), '|', "
                + "(SELECT COALESCE(SUM(score_total), 0) FROM evaluacion))", String.class);
    }
}
