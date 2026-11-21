package com.crediticio.evaluationquery.historial.infrastructure.output;

import com.crediticio.evaluationquery.historial.domain.OrigenPuntaje;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HistorialEvaluacionesJdbcAdapterTest {

    private HistorialEvaluacionesJdbcAdapter adapter;

    @BeforeEach
    void configurarBaseDeDatos() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:historial;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE solicitante (
                    id_solicitante BIGINT PRIMARY KEY,
                    nombre_completo VARCHAR(150) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE politica_clasificacion (
                    id_politica BIGINT PRIMARY KEY,
                    version INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE evaluacion (
                    id_evaluacion BIGINT PRIMARY KEY,
                    id_solicitante BIGINT NOT NULL,
                    fecha_evaluacion TIMESTAMP NOT NULL,
                    score_total INTEGER,
                    categoria VARCHAR(40),
                    estado VARCHAR(30),
                    id_politica BIGINT
                )
                """);
        jdbc.execute("""
                CREATE TABLE detalle_evaluacion (
                    id_detalle BIGINT PRIMARY KEY,
                    id_evaluacion BIGINT NOT NULL,
                    puntaje_obtenido INTEGER
                )
                """);
        jdbc.update("INSERT INTO solicitante VALUES (101, 'Ana Pérez')");
        jdbc.update("INSERT INTO politica_clasificacion VALUES (1, 3)");
        jdbc.update("""
                INSERT INTO evaluacion
                    (id_evaluacion, id_solicitante, fecha_evaluacion, score_total, categoria, estado, id_politica)
                VALUES (9001, 101, TIMESTAMP '2026-11-20 10:30:00', 720, 'BAJO', 'COMPLETADA', 1)
                """);
        jdbc.update("INSERT INTO detalle_evaluacion VALUES (1, 9001, 700)");
        jdbc.update("""
                INSERT INTO evaluacion
                    (id_evaluacion, id_solicitante, fecha_evaluacion, score_total, categoria, estado, id_politica)
                VALUES (9002, 101, TIMESTAMP '2026-11-21 10:30:00', NULL, NULL, 'PENDIENTE', NULL)
                """);
        adapter = new HistorialEvaluacionesJdbcAdapter(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void debeConsultarNombreCompletoDelSolicitante() {
        assertThat(adapter.obtenerNombreCompleto(101L)).isEqualTo("Ana Pérez");
    }

    @Test
    void debeListarHistorialSinEvaluacionesPendientes() {
        var historial = adapter.listarPorSolicitante(101L);

        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).getIdEvaluacion()).isEqualTo(9001L);
        assertThat(historial.get(0).getFechaEvaluacion())
                .isEqualTo(LocalDateTime.of(2026, 11, 20, 10, 30));
        assertThat(historial.get(0).getScoreTotal()).isEqualTo(720);
        assertThat(historial.get(0).getOrigenPuntaje()).isEqualTo(OrigenPuntaje.PERSISTIDO);
        assertThat(historial.get(0).getCategoria()).isEqualTo("BAJO");
        assertThat(historial.get(0).getVersionPolitica()).isEqualTo(3);
        assertThat(historial.get(0).getEstado()).isEqualTo("COMPLETADA");
    }
}
