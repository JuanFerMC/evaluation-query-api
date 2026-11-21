package com.crediticio.evaluationquery.reportes.domain;

import com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo.ALTO;
import static com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo.BAJO;
import static com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo.MEDIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US 7.1.1: cantidades, total consolidado y porcentajes de la distribución. */
class DistribucionRiesgoTest {

    @Test
    void conLosTresNivelesDebeCalcularCantidadesTotalYPorcentajes() {
        DistribucionRiesgo distribucion = DistribucionRiesgo.de(Map.of(BAJO, 40L, MEDIO, 35L, ALTO, 25L), 0);

        assertThat(distribucion.cantidad(BAJO)).isEqualTo(40);
        assertThat(distribucion.cantidad(MEDIO)).isEqualTo(35);
        assertThat(distribucion.cantidad(ALTO)).isEqualTo(25);
        assertThat(distribucion.total()).isEqualTo(100);
        assertThat(distribucion.porcentaje(BAJO)).isEqualByComparingTo("40.00");
        assertThat(distribucion.porcentaje(MEDIO)).isEqualByComparingTo("35.00");
        assertThat(distribucion.porcentaje(ALTO)).isEqualByComparingTo("25.00");
    }

    @Test
    void losPorcentajesDebenRedondearseADosDecimales() {
        DistribucionRiesgo distribucion = DistribucionRiesgo.de(Map.of(BAJO, 1L, MEDIO, 1L, ALTO, 1L), 0);

        assertThat(distribucion.porcentaje(BAJO)).isEqualTo(new BigDecimal("33.33"));
        DistribucionRiesgo sextos = DistribucionRiesgo.de(Map.of(BAJO, 2L, MEDIO, 1L, ALTO, 3L), 0);
        assertThat(sextos.porcentaje(MEDIO)).isEqualTo(new BigDecimal("16.67"));
        assertThat(sextos.porcentaje(ALTO)).isEqualTo(new BigDecimal("50.00"));
    }

    @Test
    void sinEvaluacionesTodoDebeSerCeroSinDividirEntreCero() {
        DistribucionRiesgo distribucion = DistribucionRiesgo.de(Map.of(), 0);

        assertThat(distribucion.total()).isZero();
        for (CategoriaRiesgo categoria : CategoriaRiesgo.values()) {
            assertThat(distribucion.cantidad(categoria)).isZero();
            assertThat(distribucion.porcentaje(categoria)).isEqualTo(new BigDecimal("0.00"));
        }
    }

    @Test
    void conUnaSolaCategoriaEstaDebeSumarElCienPorCiento() {
        DistribucionRiesgo distribucion = DistribucionRiesgo.de(Map.of(MEDIO, 7L), 0);

        assertThat(distribucion.total()).isEqualTo(7);
        assertThat(distribucion.porcentaje(MEDIO)).isEqualByComparingTo("100");
        assertThat(distribucion.porcentaje(BAJO)).isEqualByComparingTo("0");
        assertThat(distribucion.porcentaje(ALTO)).isEqualByComparingTo("0");
    }

    @Test
    void lasSinClasificarSeInformanAparteYNoEntranEnElTotalNiEnLosPorcentajes() {
        DistribucionRiesgo distribucion = DistribucionRiesgo.de(Map.of(BAJO, 1L, ALTO, 1L), 5);

        assertThat(distribucion.total()).isEqualTo(2);
        assertThat(distribucion.getSinClasificar()).isEqualTo(5);
        assertThat(distribucion.porcentaje(BAJO)).isEqualByComparingTo("50");
    }

    @Test
    void noDebeAceptarCantidadesNegativas() {
        assertThatThrownBy(() -> DistribucionRiesgo.de(Map.of(BAJO, -1L), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DistribucionRiesgo.de(Map.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
