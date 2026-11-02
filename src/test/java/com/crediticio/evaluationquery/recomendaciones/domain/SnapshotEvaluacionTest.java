package com.crediticio.evaluationquery.recomendaciones.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrato del snapshot recibido desde scoring-api: invariantes y copia defensiva. */
class SnapshotEvaluacionTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 18, 10, 0);

    private SnapshotEvaluacion.Factor factor(int orden, long detalle, int contribucion) {
        return new SnapshotEvaluacion.Factor(orden, detalle, 201, "NUMERO_MORAS", ">", "2",
                -20, true, contribucion);
    }

    private SnapshotEvaluacion valido(List<SnapshotEvaluacion.Factor> factores) {
        return new SnapshotEvaluacion(21, 12, FECHA, -20, "MEDIO", 1, "PENDIENTE", factores);
    }

    @Test
    void aceptaUnSnapshotValidoYConservaSusDatos() {
        SnapshotEvaluacion.Factor factor = factor(1, 101, -20);

        SnapshotEvaluacion snapshot = valido(List.of(factor));

        assertThat(snapshot.idEvaluacion()).isEqualTo(21);
        assertThat(snapshot.idSolicitante()).isEqualTo(12);
        assertThat(snapshot.fechaEvaluacion()).isEqualTo(FECHA);
        assertThat(snapshot.scoreTotal()).isEqualTo(-20);
        assertThat(snapshot.categoria()).isEqualTo("MEDIO");
        assertThat(snapshot.versionPolitica()).isEqualTo(1);
        assertThat(snapshot.estado()).isEqualTo("PENDIENTE");
        assertThat(snapshot.factores()).containsExactly(factor);
    }

    @Test
    void conservaUnaCopiaInmutableDeLaListaDeFactores() {
        List<SnapshotEvaluacion.Factor> factores = new ArrayList<>(List.of(factor(1, 101, -20)));

        SnapshotEvaluacion snapshot = valido(factores);
        factores.clear();

        assertThat(snapshot.factores()).hasSize(1);
        assertThatThrownBy(() -> snapshot.factores().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rechazaIdentificadoresYVersionDePoliticaNoPositivos() {
        assertThatThrownBy(() -> new SnapshotEvaluacion(0, 12, FECHA, -20, "MEDIO", 1, "PENDIENTE",
                List.of(factor(1, 101, -20)))).isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> new SnapshotEvaluacion(21, -1, FECHA, -20, "MEDIO", 1, "PENDIENTE",
                List.of(factor(1, 101, -20)))).isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> new SnapshotEvaluacion(21, 12, FECHA, -20, "MEDIO", 0, "PENDIENTE",
                List.of(factor(1, 101, -20)))).isInstanceOf(SnapshotEvaluacionInvalidoException.class);
    }

    @Test
    void rechazaFechaCategoriaYEstadoInvalidos() {
        assertThatThrownBy(() -> new SnapshotEvaluacion(21, 12, null, -20, "MEDIO", 1, "PENDIENTE",
                List.of(factor(1, 101, -20)))).isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> new SnapshotEvaluacion(21, 12, FECHA, -20, "CRITICO", 1, "PENDIENTE",
                List.of(factor(1, 101, -20)))).isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> new SnapshotEvaluacion(21, 12, FECHA, -20, "MEDIO", 1, "COMPLETADA",
                List.of(factor(1, 101, -20)))).isInstanceOf(SnapshotEvaluacionInvalidoException.class);
    }

    @Test
    void rechazaListaNulaFactoresNulosYPuntajeInconsistente() {
        assertThatThrownBy(() -> new SnapshotEvaluacion(21, 12, FECHA, -20, "MEDIO", 1, "PENDIENTE", null))
                .isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> valido(java.util.Arrays.asList((SnapshotEvaluacion.Factor) null)))
                .isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> new SnapshotEvaluacion(21, 12, FECHA, -10, "MEDIO", 1, "PENDIENTE",
                List.of(factor(1, 101, -20)))).isInstanceOf(SnapshotEvaluacionInvalidoException.class);
    }

    @Test
    void rechazaFactoresFueraDeOrdenYDetallesDuplicados() {
        assertThatThrownBy(() -> valido(List.of(factor(2, 101, -20))))
                .isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> new SnapshotEvaluacion(21, 12, FECHA, -40, "MEDIO", 1, "PENDIENTE",
                List.of(factor(1, 101, -20), factor(2, 101, -20))))
                .isInstanceOf(SnapshotEvaluacionInvalidoException.class);
    }

    @Test
    void aceptaFactorNoCumplidoSoloConContribucionCero() {
        SnapshotEvaluacion.Factor factor = new SnapshotEvaluacion.Factor(1, 101, 201,
                "NUMERO_MORAS", "<=", "2", -20, false, 0);

        assertThat(factor.contribucion()).isZero();
        assertThat(factor.condicionCumplida()).isFalse();
    }

    @Test
    void rechazaFactoresQueNoCumplenElContratoOContribucionInconsistente() {
        assertThatThrownBy(() -> new SnapshotEvaluacion.Factor(0, 101, 201,
                "NUMERO_MORAS", ">", "2", -20, true, -20))
                .isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> new SnapshotEvaluacion.Factor(1, 101, 201,
                " ", ">", "2", -20, true, -20))
                .isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> new SnapshotEvaluacion.Factor(1, 101, 201,
                "NUMERO_MORAS", "LIKE", "2", -20, true, -20))
                .isInstanceOf(SnapshotEvaluacionInvalidoException.class);
        assertThatThrownBy(() -> new SnapshotEvaluacion.Factor(1, 101, 201,
                "NUMERO_MORAS", ">", "2", -20, true, 0))
                .isInstanceOf(SnapshotEvaluacionInvalidoException.class);
    }
}
