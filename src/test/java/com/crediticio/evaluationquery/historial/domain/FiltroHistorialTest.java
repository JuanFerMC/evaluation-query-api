package com.crediticio.evaluationquery.historial.domain;

import com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US 5.1.2: límites de fechas, validación del rango y normalización del documento; paginación. */
class FiltroHistorialTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 11, 30);

    @Test
    void lasFechasDebenCubrirDiasCompletosEInclusivos() {
        FiltroHistorial filtro = new FiltroHistorial(INICIO, FIN, null, CategoriaRiesgo.ALTO);

        assertThat(filtro.inicioInclusivo()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(filtro.finExclusivo()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
    }

    @Test
    void sinFechasNoDebeHaberLimites() {
        FiltroHistorial filtro = FiltroHistorial.sinFiltros();

        assertThat(filtro.inicioInclusivo()).isNull();
        assertThat(filtro.finExclusivo()).isNull();
    }

    @Test
    void unRangoDeUnSoloDiaEsValido() {
        assertThat(new FiltroHistorial(INICIO, INICIO, null, null).finExclusivo())
                .isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
    }

    @Test
    void laFechaInicialNoPuedeSerPosteriorALaFinal() {
        assertThatThrownBy(() -> new FiltroHistorial(FIN, INICIO, null, null))
                .isInstanceOf(FiltroHistorialInvalidoException.class)
                .hasMessageContaining("fechaDesde");
    }

    @Test
    void elDocumentoDebeRecortarseYVacioNoFiltra() {
        assertThat(new FiltroHistorial(null, null, " 900000301 ", null).numeroDocumento()).isEqualTo("900000301");
        assertThat(new FiltroHistorial(null, null, "   ", null).numeroDocumento()).isNull();
    }

    @Test
    void laPaginacionDebeValidarseYCalcularElDesplazamientoYLasPaginas() {
        Paginacion paginacion = new Paginacion(2, 3);

        assertThat(paginacion.desplazamiento()).isEqualTo(6);
        assertThat(paginacion.totalPaginas(0)).isZero();
        assertThat(paginacion.totalPaginas(6)).isEqualTo(2);
        assertThat(paginacion.totalPaginas(7)).isEqualTo(3);
        assertThat(new Paginacion(Integer.MAX_VALUE, 100).desplazamiento()).isEqualTo(Integer.MAX_VALUE * 100L);
        assertThatThrownBy(() -> new Paginacion(-1, 20)).isInstanceOf(FiltroHistorialInvalidoException.class);
        assertThatThrownBy(() -> new Paginacion(0, 0)).isInstanceOf(FiltroHistorialInvalidoException.class);
        assertThatThrownBy(() -> new Paginacion(0, Paginacion.TAMANO_MAXIMO + 1))
                .isInstanceOf(FiltroHistorialInvalidoException.class);
    }
}
