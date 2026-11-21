package com.crediticio.evaluationquery.historial.application;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;
import com.crediticio.evaluationquery.historial.application.dto.PaginaHistorialResponse;
import com.crediticio.evaluationquery.historial.domain.EvaluacionHistorica;
import com.crediticio.evaluationquery.historial.domain.FiltroHistorial;
import com.crediticio.evaluationquery.historial.domain.Paginacion;
import com.crediticio.evaluationquery.historial.ports.output.BusquedaHistorialPort;
import com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** US 5.1.2 con el puerto simulado: total, página vacía y mismo filtro para el conteo y la página. */
class FiltrarHistorialServiceTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 15, 10, 0);

    /** Puerto simulado que registra los filtros recibidos por cada operación. */
    private static final class BusquedaSimulada implements BusquedaHistorialPort {

        private final long total;
        private final List<EvaluacionHistorica> pagina;
        private final List<FiltroHistorial> filtrosContados = new ArrayList<>();
        private final List<FiltroHistorial> filtrosBuscados = new ArrayList<>();

        BusquedaSimulada(long total, List<EvaluacionHistorica> pagina) {
            this.total = total;
            this.pagina = pagina;
        }

        @Override
        public List<EvaluacionHistorica> buscar(FiltroHistorial filtro, Paginacion paginacion) {
            filtrosBuscados.add(filtro);
            return pagina;
        }

        @Override
        public long contar(FiltroHistorial filtro) {
            filtrosContados.add(filtro);
            return total;
        }
    }

    @Test
    void debeDevolverLaPaginaConElTotalYAplicarElMismoFiltroAlConteoYALaBusqueda() {
        BusquedaSimulada puerto = new BusquedaSimulada(7, List.of(
                EvaluacionHistorica.reconstruir(9, FECHA, 40, 0, "ALTO", 1, "COMPLETADA"),
                EvaluacionHistorica.reconstruir(4, FECHA.minusDays(1), 35, 0, "ALTO", 1, "COMPLETADA")));
        FiltroHistorial filtro = new FiltroHistorial(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null,
                CategoriaRiesgo.ALTO);

        PaginaHistorialResponse respuesta = new FiltrarHistorialService(puerto, new HistorialMapper())
                .filtrar(filtro, new Paginacion(1, 2));

        assertThat(respuesta.evaluaciones()).extracting(EvaluacionHistorialResponse::idEvaluacion)
                .containsExactly(9L, 4L);
        assertThat(respuesta.evaluaciones().get(0).explicacion()).isEqualTo("/api/v1/evaluaciones/9/explicacion");
        assertThat(respuesta.pagina()).isEqualTo(1);
        assertThat(respuesta.tamano()).isEqualTo(2);
        assertThat(respuesta.totalRegistros()).isEqualTo(7);
        assertThat(respuesta.totalPaginas()).isEqualTo(4);
        assertThat(puerto.filtrosContados).containsExactly(filtro);
        assertThat(puerto.filtrosBuscados).containsExactly(filtro);
    }

    @Test
    void sinCoincidenciasDebeDevolverUnaPaginaVaciaConTotalCeroSinLeerFilas() {
        BusquedaSimulada puerto = new BusquedaSimulada(0, List.of());

        PaginaHistorialResponse respuesta = new FiltrarHistorialService(puerto, new HistorialMapper())
                .filtrar(FiltroHistorial.sinFiltros(), new Paginacion(0, 20));

        assertThat(respuesta.evaluaciones()).isEmpty();
        assertThat(respuesta.totalRegistros()).isZero();
        assertThat(respuesta.totalPaginas()).isZero();
        assertThat(puerto.filtrosBuscados).isEmpty();
    }

    @Test
    void unaPaginaPosteriorALaUltimaDebeVenirVaciaConElTotalReal() {
        BusquedaSimulada puerto = new BusquedaSimulada(4, List.of());

        PaginaHistorialResponse respuesta = new FiltrarHistorialService(puerto, new HistorialMapper())
                .filtrar(FiltroHistorial.sinFiltros(), new Paginacion(2, 2));

        assertThat(respuesta.evaluaciones()).isEmpty();
        assertThat(respuesta.totalRegistros()).isEqualTo(4);
        assertThat(respuesta.totalPaginas()).isEqualTo(2);
        assertThat(puerto.filtrosBuscados).isEmpty();
    }
}
