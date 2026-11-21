package com.crediticio.evaluationquery;

import com.crediticio.evaluationquery.explicacion.domain.ExplicacionEvaluacion;
import com.crediticio.evaluationquery.explicacion.domain.FactorAplicado;
import com.crediticio.evaluationquery.explicacion.domain.OrigenPuntaje;
import com.crediticio.evaluationquery.explicacion.infrastructure.output.ExplicacionEvaluacionJdbcAdapter;
import com.crediticio.evaluationquery.historial.domain.EvaluacionHistorica;
import com.crediticio.evaluationquery.historial.domain.FiltroHistorial;
import com.crediticio.evaluationquery.historial.domain.Paginacion;
import com.crediticio.evaluationquery.historial.infrastructure.output.BusquedaHistorialJdbcAdapter;
import com.crediticio.evaluationquery.historial.infrastructure.output.HistorialEvaluacionesJdbcAdapter;
import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionParaRecomendar;
import com.crediticio.evaluationquery.recomendaciones.domain.FactorEvaluado;
import com.crediticio.evaluationquery.recomendaciones.domain.GeneracionConcurrenteException;
import com.crediticio.evaluationquery.recomendaciones.domain.GeneracionRecomendaciones;
import com.crediticio.evaluationquery.recomendaciones.domain.RecomendacionGenerada;
import com.crediticio.evaluationquery.recomendaciones.domain.TipoActivacion;
import com.crediticio.evaluationquery.recomendaciones.infrastructure.output.EvaluacionParaRecomendarJdbcAdapter;
import com.crediticio.evaluationquery.recomendaciones.infrastructure.output.RecomendacionesJdbcAdapter;
import com.crediticio.evaluationquery.reportes.domain.DistribucionRiesgo;
import com.crediticio.evaluationquery.reportes.infrastructure.output.DistribucionRiesgoJdbcAdapter;
import com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo;
import com.crediticio.evaluationquery.support.DatosScoringPrueba;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato de HU13, HU14 y HU15 contra el esquema real de scoring-api en PostgreSQL. En un esquema temporal y
 * aislado aplica las migraciones V1–V11 de scoring-api (leídas de ../scoring-api/src/main/resources/db/migration,
 * sin modificarlas, sustituyendo el placeholder ${flyway:defaultSchema}) y la migración V1 propia de esta API;
 * inserta una evaluación anterior a HU12 antes de V8, evaluaciones clasificadas después (respetando los triggers
 * de V8) y una evaluación PENDIENTE con su trabajo después de V11 (respetando sus triggers). Ejecuta los
 * adaptadores de lectura dentro de transacciones de solo lectura, y el de recomendaciones con inserciones
 * reales, concurrencia y los triggers de inmutabilidad. No toca el esquema public ni sus datos.
 *
 * Antes de abrir cualquier conexión exige, por igualdad exacta de texto, -Dit.base-dedicada=motor_scoring_it y
 * -Dspring.datasource.url=jdbc:postgresql://localhost:5432/motor_scoring_it (nunca usa variables de la
 * aplicación). Exige que el esquema no exista. Limpieza: solo elimina el esquema si conserva el OID y el
 * comentario-marca registrados al crearlo en esta ejecución; si no, no elimina nada y hace fallar la prueba.
 *
 * Al terminar en "IT", Surefire no la ejecuta con `./mvnw test`. Ejecución manual (requiere autorización
 * expresa para usar motor_scoring_it), desde la raíz de evaluation-query-api, con scoring-api en la carpeta
 * hermana y DB_USERNAME y DB_PASSWORD en el entorno:
 *   mvnw.cmd -B test "-Dtest=EsquemaScoringContratoIT" "-Dit.base-dedicada=motor_scoring_it"
 *     "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/motor_scoring_it"
 */
class EsquemaScoringContratoIT {

    private static final String ESQUEMA = "eq_contrato_it";
    private static final String BASE_AUTORIZADA = "motor_scoring_it";
    private static final String URL_AUTORIZADA = "jdbc:postgresql://localhost:5432/" + BASE_AUTORIZADA;
    private static final Path MIGRACIONES_SCORING = Path.of("..", "scoring-api", "src", "main", "resources", "db",
            "migration");
    private static final Path MIGRACION_PROPIA = Path.of("src", "main", "resources", "db", "migration",
            "V1__crear_recomendaciones.sql");
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 10, 10, 0);

    private static String url;
    private static String usuario;
    private static String clave;
    private static String marca;
    private static Long oidEsquemaCreado;

    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transaccion;
    private static TransactionTemplate soloLectura;
    private static ExplicacionEvaluacionJdbcAdapter explicacionAdapter;
    private static HistorialEvaluacionesJdbcAdapter historialAdapter;
    private static BusquedaHistorialJdbcAdapter busquedaAdapter;
    private static DistribucionRiesgoJdbcAdapter distribucionAdapter;
    private static EvaluacionParaRecomendarJdbcAdapter evaluacionParaRecomendarAdapter;
    private static RecomendacionesJdbcAdapter recomendacionesAdapter;

    private static long idSolicitante;
    private static long idSolicitanteSinEvaluaciones;
    private static long idAnteriorAHu12;
    private static long idClasificada;
    private static long idMismaFecha;
    private static long idPendiente;

    @BeforeAll
    static void prepararEsquemaConMigracionesDeScoring() throws Exception {
        String base = System.getProperty("it.base-dedicada");
        url = System.getProperty("spring.datasource.url");
        usuario = System.getenv("DB_USERNAME");
        clave = System.getenv("DB_PASSWORD");
        if (!BASE_AUTORIZADA.equals(base)) {
            throw new IllegalStateException("Debe indicarse -Dit.base-dedicada=" + BASE_AUTORIZADA);
        }
        if (!URL_AUTORIZADA.equals(url)) {
            throw new IllegalStateException("spring.datasource.url debe indicarse con -D y ser exactamente "
                    + URL_AUTORIZADA);
        }
        assertThat(usuario).as("DB_USERNAME").isNotBlank();
        assertThat(clave).as("DB_PASSWORD").isNotBlank();
        assertThat(MIGRACIONES_SCORING.resolve("V11__saga_recomendaciones.sql"))
                .as("migraciones de scoring-api en la carpeta hermana").exists();

        crearEsquemaPropio(base);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(url + "?currentSchema=" + ESQUEMA, usuario,
                clave);
        jdbcTemplate = new JdbcTemplate(dataSource);
        DataSourceTransactionManager gestor = new DataSourceTransactionManager(dataSource);
        transaccion = new TransactionTemplate(gestor);
        soloLectura = new TransactionTemplate(gestor);
        soloLectura.setReadOnly(true);
        NamedParameterJdbcTemplate consultas = new NamedParameterJdbcTemplate(dataSource);
        explicacionAdapter = new ExplicacionEvaluacionJdbcAdapter(consultas);
        historialAdapter = new HistorialEvaluacionesJdbcAdapter(consultas);
        busquedaAdapter = new BusquedaHistorialJdbcAdapter(consultas);
        distribucionAdapter = new DistribucionRiesgoJdbcAdapter(consultas);
        evaluacionParaRecomendarAdapter = new EvaluacionParaRecomendarJdbcAdapter(consultas);
        recomendacionesAdapter = new RecomendacionesJdbcAdapter(consultas, ESQUEMA);
        DatosScoringPrueba datos = new DatosScoringPrueba(jdbcTemplate);

        aplicarMigraciones(1, 7);
        long[] ids = new long[4];
        transaccion.executeWithoutResult(estado -> {
            idSolicitante = datos.solicitante("Solicitante Contrato", "900000201");
            idSolicitanteSinEvaluaciones = datos.solicitante("Sin Evaluaciones", "900000202");
            ids[0] = datos.riesgo("NUMERO_MORAS");
            ids[1] = datos.riesgo("INGRESOS_MENSUALES");
            ids[2] = datos.regla(ids[0], ">", "2", -20);
            ids[3] = datos.regla(ids[1], ">=", "3000000", 20);
            idAnteriorAHu12 = datos.evaluacionAnteriorAHu12(idSolicitante, FECHA.minusDays(9));
            datos.detalle(idAnteriorAHu12, ids[3], ">=", "3000000", 20, true, 20);
        });

        aplicarMigraciones(8, 9);
        transaccion.executeWithoutResult(estado -> {
            long politica = datos.politica(1);
            jdbcTemplate.update("INSERT INTO rango_clasificacion (id_politica, categoria, limite_inferior, "
                    + "limite_superior) VALUES (?, 'BAJO', -100, -30), (?, 'MEDIO', -29, 29), (?, 'ALTO', 30, 130)",
                    politica, politica, politica);
            // puntaje -20 = suma de detalles (trigger A); -20 pertenece a MEDIO en esta política (trigger B)
            idClasificada = datos.evaluacionClasificada(idSolicitante, FECHA, -20, "MEDIO", politica);
            datos.detalle(idClasificada, ids[2], ">", "2", -20, true, -20);
            datos.detalle(idClasificada, ids[3], ">=", "3000000", 20, false, 0);
            idMismaFecha = datos.evaluacionClasificada(idSolicitante, FECHA, 0, "MEDIO", politica);
        });
        // La regla se edita después de evaluar; la explicación debe mostrar la versión aplicada
        jdbcTemplate.update("UPDATE regla_scoring SET puntaje = -30, valor_condicion = '4' WHERE id_regla = ?", ids[2]);

        aplicarMigraciones(10, 11);
        aplicarMigracionPropia();
        transaccion.executeWithoutResult(estado -> {
            long politica = jdbcTemplate.queryForObject("SELECT max(id_politica) FROM politica_clasificacion",
                    Long.class);
            // V11: toda evaluación nueva nace PENDIENTE junto con su trabajo (concesión vigente, un intento)
            idPendiente = datos.evaluacionConEstado(idSolicitante, FECHA.plusDays(1), -20, "MEDIO", politica,
                    "PENDIENTE");
            datos.detalle(idPendiente, ids[2], ">", "4", -30, false, 0);
            datos.detalle(idPendiente, ids[3], ">=", "3000000", 20, false, 0);
            datos.detalle(idPendiente, ids[2], ">", "1", -20, true, -20);
            jdbcTemplate.update("INSERT INTO trabajo_recomendacion (id_evaluacion, intentos, intentos_totales, "
                    + "proximo_intento, id_concesion, concesion_hasta) VALUES (?, 1, 1, clock_timestamp(), ?, "
                    + "clock_timestamp() + interval '30 seconds')", idPendiente, UUID.randomUUID());
        });
    }

    @AfterAll
    static void eliminarSoloElEsquemaCreadoPorLaPrueba() {
        if (marca == null) {
            return; // la prueba abortó antes de crear nada
        }
        String motivo;
        try (Connection connection = DriverManager.getConnection(url, usuario, clave)) {
            connection.setAutoCommit(false);
            try {
                motivo = motivoParaNoEliminarElEsquema(connection);
                if (motivo == null) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("DROP SCHEMA " + ESQUEMA + " CASCADE");
                    }
                    connection.commit();
                    return;
                }
                connection.rollback();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            motivo = "falló la comprobación o el DROP; transacción revertida (SQLState " + ex.getSQLState() + ")";
        }
        throw new AssertionError("No se eliminó el esquema " + ESQUEMA + ": " + motivo
                + ". Revíselo manualmente antes de volver a ejecutar la prueba.");
    }

    @Test
    void lasMigracionesDeScoringQuedanAplicadasEnElEsquemaTemporal() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = ? "
                + "AND table_name = 'evaluacion' AND column_name IN ('score_total', 'categoria', 'id_politica')",
                Long.class, ESQUEMA)).isEqualTo(3L);
    }

    @Test
    void laExplicacionUsaLasInstantaneasPersistidasYCoincideConElPuntaje() {
        ExplicacionEvaluacion explicacion = enSoloLectura(() -> explicacionAdapter.buscarPorId(idClasificada)).orElseThrow();

        assertThat(explicacion.getScoreTotal()).isEqualTo(-20);
        assertThat(explicacion.getOrigenPuntaje()).isEqualTo(OrigenPuntaje.PERSISTIDO);
        assertThat(explicacion.getCategoria()).isEqualTo("MEDIO");
        assertThat(explicacion.getVersionPolitica()).isEqualTo(1);
        assertThat(explicacion.getFechaEvaluacion()).isEqualTo(FECHA);
        assertThat(explicacion.getFactores()).containsExactly(
                new FactorAplicado(explicacion.getFactores().get(0).idRegla(), "NUMERO_MORAS", ">", "2", -20, true, -20),
                new FactorAplicado(explicacion.getFactores().get(1).idRegla(), "INGRESOS_MENSUALES", ">=", "3000000",
                        20, false, 0));
    }

    @Test
    void unaEvaluacionAnteriorAHu12SeExplicaConPuntajeDerivado() {
        ExplicacionEvaluacion explicacion = enSoloLectura(() -> explicacionAdapter.buscarPorId(idAnteriorAHu12))
                .orElseThrow();

        assertThat(explicacion.getScoreTotal()).isEqualTo(20);
        assertThat(explicacion.getOrigenPuntaje()).isEqualTo(OrigenPuntaje.DERIVADO_DE_DETALLES);
        assertThat(explicacion.isClasificada()).isFalse();
    }

    @Test
    void unaEvaluacionInexistenteNoDevuelveExplicacion() {
        assertThat(enSoloLectura(() -> explicacionAdapter.buscarPorId(Long.MAX_VALUE))).isEmpty();
    }

    @Test
    void elHistorialSeOrdenaPorFechaYDesempataPorIdentificador() {
        List<EvaluacionHistorica> historial = enSoloLectura(() -> historialAdapter.listarPorSolicitante(idSolicitante));

        assertThat(historial).extracting(EvaluacionHistorica::getIdEvaluacion)
                .containsExactly(idMismaFecha, idClasificada, idAnteriorAHu12);
        assertThat(historial.get(1).getScoreTotal()).isEqualTo(-20);
        assertThat(historial.get(2).getScoreTotal()).isEqualTo(20);
        assertThat(historial.get(2).isClasificada()).isFalse();
    }

    @Test
    void laExistenciaDelSolicitanteSeDistingueDelHistorialVacio() {
        assertThat(enSoloLectura(() -> historialAdapter.existe(idSolicitanteSinEvaluaciones))).isTrue();
        assertThat(enSoloLectura(() -> historialAdapter.listarPorSolicitante(idSolicitanteSinEvaluaciones))).isEmpty();
        assertThat(enSoloLectura(() -> historialAdapter.existe(Long.MAX_VALUE))).isFalse();
    }

    // ---- US 5.1.2: historial filtrado y paginado ----

    @Test
    void laBusquedaFiltradaYSuConteoUsanLosMismosFiltrosEnPostgresql() {
        FiltroHistorial filtro = new FiltroHistorial(FECHA.toLocalDate(), FECHA.toLocalDate().plusDays(1),
                " 900000201 ", CategoriaRiesgo.MEDIO);

        assertThat(enSoloLectura(() -> busquedaAdapter.buscar(filtro, new Paginacion(0, 20))))
                .extracting(EvaluacionHistorica::getIdEvaluacion)
                .containsExactly(idMismaFecha, idClasificada); // la PENDIENTE del día siguiente no aparece
        assertThat(enSoloLectura(() -> busquedaAdapter.contar(filtro))).isEqualTo(2);
        assertThat(enSoloLectura(() -> busquedaAdapter.buscar(filtro, new Paginacion(1, 1))))
                .extracting(EvaluacionHistorica::getIdEvaluacion).containsExactly(idClasificada);

        FiltroHistorial sinClasificar = new FiltroHistorial(null, FECHA.toLocalDate().minusDays(9), "900000201", null);
        List<EvaluacionHistorica> anteriores = enSoloLectura(() -> busquedaAdapter.buscar(sinClasificar,
                new Paginacion(0, 20)));
        assertThat(anteriores).extracting(EvaluacionHistorica::getIdEvaluacion).containsExactly(idAnteriorAHu12);
        assertThat(anteriores.get(0).getScoreTotal()).isEqualTo(20);

        FiltroHistorial sinCoincidencias = new FiltroHistorial(null, null, "900000202", null);
        assertThat(enSoloLectura(() -> busquedaAdapter.buscar(sinCoincidencias, new Paginacion(0, 20)))).isEmpty();
        assertThat(enSoloLectura(() -> busquedaAdapter.contar(sinCoincidencias))).isZero();
    }

    // ---- US 7.1.1: distribución por nivel de riesgo ----

    @Test
    void laDistribucionCuentaCadaEvaluacionPersistidaUnaVezEnPostgresql() {
        DistribucionRiesgo distribucion = enSoloLectura(distribucionAdapter::contarPorCategoria);

        for (CategoriaRiesgo categoria : CategoriaRiesgo.values()) {
            assertThat(distribucion.cantidad(categoria)).as(categoria.name()).isEqualTo(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM evaluacion WHERE categoria = ?", Long.class, categoria.name()));
        }
        assertThat(distribucion.total()).isEqualTo(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evaluacion WHERE categoria IS NOT NULL", Long.class));
        assertThat(distribucion.getSinClasificar()).isEqualTo(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evaluacion WHERE categoria IS NULL", Long.class));
        // incluye la PENDIENTE (categoría ya persistida) y la anterior a HU12 queda fuera del total
        assertThat(distribucion.cantidad(CategoriaRiesgo.MEDIO)).isGreaterThanOrEqualTo(3);
        assertThat(distribucion.getSinClasificar()).isGreaterThanOrEqualTo(1);
    }

    // ---- HU14 y estado de la saga (V11 de scoring-api y V1 propia) ----

    @Test
    void elHistorialExcluyeLaEvaluacionPendienteYLaExplicacionLaIdentifica() {
        assertThat(enSoloLectura(() -> historialAdapter.listarPorSolicitante(idSolicitante)))
                .extracting(EvaluacionHistorica::getIdEvaluacion).doesNotContain(idPendiente);
        assertThat(enSoloLectura(() -> explicacionAdapter.buscarPorId(idPendiente)).orElseThrow().isPendiente())
                .isTrue();
        assertThat(enSoloLectura(() -> explicacionAdapter.buscarPorId(idClasificada)).orElseThrow().isPendiente())
                .isFalse();
    }

    @Test
    void laGeneracionLeeElEstadoLaClasificacionYLosFactoresPersistidos() {
        EvaluacionParaRecomendar evaluacion = enSoloLectura(() -> evaluacionParaRecomendarAdapter.buscar(idPendiente))
                .orElseThrow();

        assertThat(evaluacion.esPendiente()).isTrue();
        assertThat(evaluacion.categoria()).isEqualTo("MEDIO");
        assertThat(evaluacion.factores()).containsExactly(
                new FactorEvaluado("NUMERO_MORAS", false, 0),
                new FactorEvaluado("INGRESOS_MENSUALES", false, 0),
                new FactorEvaluado("NUMERO_MORAS", true, -20));
        assertThat(enSoloLectura(() -> evaluacionParaRecomendarAdapter.buscar(idAnteriorAHu12)).orElseThrow()
                .esAnteriorALaSaga()).isTrue();
    }

    @Test
    void lasRecomendacionesSeGuardanUnaVezYSonInmutables() {
        long id = 900_001L;
        GeneracionRecomendaciones guardada = transaccion.execute(estado -> recomendacionesAdapter.guardar(
                generacion(id, "V1", "Texto de la versión 1")));

        assertThat(guardada.fechaGeneracion()).isNotNull();
        assertThat(guardada.recomendaciones()).extracting(RecomendacionGenerada::codigo)
                .containsExactly("REC-CAT-MEDIO-01", "REC-MORAS-01");
        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> recomendacionesAdapter.guardar(
                generacion(id, "V2", "Otro texto")))).isInstanceOf(GeneracionConcurrenteException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE recomendacion_evaluacion SET texto = 'x' "
                + "WHERE id_evaluacion = ?", id)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("inmutables");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM generacion_recomendaciones WHERE id_evaluacion = ?",
                id)).isInstanceOf(DataAccessException.class).hasMessageContaining("inmutables");
        assertThat(enSoloLectura(() -> recomendacionesAdapter.buscar(id)).orElseThrow().recomendaciones().get(1).texto())
                .isEqualTo("Texto de la versión 1");
    }

    /** La segunda inserción espera a que confirme la primera y falla por clave duplicada, sin duplicar filas. */
    @Test
    void dosGeneracionesSimultaneasDeLaMismaEvaluacionGuardanSoloUna() throws Exception {
        long id = 900_002L;
        CountDownLatch primeraInsertada = new CountDownLatch(1);
        CompletableFuture<Void> primera = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            recomendacionesAdapter.guardar(generacion(id, "V1", "Primera"));
            primeraInsertada.countDown();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(primeraInsertada.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> recomendacionesAdapter.guardar(
                generacion(id, "V1", "Segunda")))).isInstanceOf(GeneracionConcurrenteException.class);
        primera.get(10, TimeUnit.SECONDS);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM recomendacion_evaluacion WHERE id_evaluacion = ?",
                Long.class, id)).isEqualTo(2L);
        assertThat(enSoloLectura(() -> recomendacionesAdapter.buscar(id)).orElseThrow().recomendaciones().get(1).texto())
                .isEqualTo("Primera");
    }

    private static GeneracionRecomendaciones generacion(long id, String version, String texto) {
        return new GeneracionRecomendaciones(id, version, "MEDIO", null, List.of(
                new RecomendacionGenerada(1, "REC-CAT-MEDIO-01", "Validar factores", TipoActivacion.CATEGORIA,
                        "MEDIO", null),
                new RecomendacionGenerada(2, "REC-MORAS-01", texto, TipoActivacion.FACTOR_DESFAVORABLE, null,
                        "NUMERO_MORAS")));
    }

    // ---------------------------------------------------------------------------------------

    private static <T> T enSoloLectura(Supplier<T> consulta) {
        return soloLectura.execute(estado -> consulta.get());
    }

    private static void crearEsquemaPropio(String base) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, usuario, clave);
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT current_database(), EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = ?)")) {
            statement.setString(1, ESQUEMA);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                assertThat(resultSet.getString(1)).as("base de la conexión").isEqualTo(base);
                assertThat(resultSet.getBoolean(2))
                        .as("el esquema " + ESQUEMA + " ya existe; no se reutiliza ni se elimina").isFalse();
            }
            String nuevaMarca = "creado por EsquemaScoringContratoIT " + UUID.randomUUID();
            try (Statement creacion = connection.createStatement()) {
                creacion.execute("CREATE SCHEMA " + ESQUEMA);
                creacion.execute("COMMENT ON SCHEMA " + ESQUEMA + " IS '" + nuevaMarca + "'");
            }
            marca = nuevaMarca;
            try (PreparedStatement oid = connection.prepareStatement("SELECT oid FROM pg_namespace WHERE nspname = ?")) {
                oid.setString(1, ESQUEMA);
                try (ResultSet resultSet = oid.executeQuery()) {
                    resultSet.next();
                    oidEsquemaCreado = resultSet.getLong(1);
                }
            }
        }
    }

    /** Ejecuta en orden las migraciones Vdesde..Vhasta de scoring-api, sin modificarlas en disco. */
    private static void aplicarMigraciones(int desde, int hasta) throws IOException {
        List<Path> archivos;
        try (Stream<Path> listado = Files.list(MIGRACIONES_SCORING)) {
            archivos = listado.filter(archivo -> {
                int version = version(archivo);
                return version >= desde && version <= hasta;
            }).sorted((a, b) -> Integer.compare(version(a), version(b))).toList();
        }
        assertThat(archivos).as("migraciones V" + desde + "–V" + hasta).hasSize(hasta - desde + 1);
        for (Path archivo : archivos) {
            String sql = Files.readString(archivo, StandardCharsets.UTF_8).replace("${flyway:defaultSchema}", ESQUEMA);
            transaccion.executeWithoutResult(estado -> jdbcTemplate.execute(sql));
        }
    }

    /** Migración V1 de esta API (recomendaciones), en el mismo esquema temporal. */
    private static void aplicarMigracionPropia() throws IOException {
        String sql = Files.readString(MIGRACION_PROPIA, StandardCharsets.UTF_8).replace("${flyway:defaultSchema}",
                ESQUEMA);
        transaccion.executeWithoutResult(estado -> jdbcTemplate.execute(sql));
    }

    private static int version(Path archivo) {
        String nombre = archivo.getFileName().toString();
        return Integer.parseInt(nombre.substring(1, nombre.indexOf("__")));
    }

    private static String motivoParaNoEliminarElEsquema(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT n.oid, obj_description(n.oid, 'pg_namespace') FROM pg_namespace n WHERE n.nspname = ?")) {
            statement.setString(1, ESQUEMA);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return "el esquema ya no existe";
                }
                if (oidEsquemaCreado == null || resultSet.getLong(1) != oidEsquemaCreado) {
                    return "el esquema existente no es el que creó esta ejecución (OID distinto)";
                }
                if (!marca.equals(resultSet.getString(2))) {
                    return "el esquema no conserva la marca de creación de esta ejecución";
                }
            }
        }
        return null;
    }
}
