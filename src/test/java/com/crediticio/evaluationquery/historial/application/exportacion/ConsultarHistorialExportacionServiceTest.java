package com.crediticio.evaluationquery.historial.application.exportacion;

import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import com.crediticio.evaluationquery.historial.domain.SolicitanteNoEncontradoException;
import com.crediticio.evaluationquery.historial.ports.input.ConsultarHistorialUseCase;
import com.crediticio.evaluationquery.shared.exception.IdentificadorInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias del servicio que prepara historiales individuales y múltiples
 * para las exportaciones US 5.2.1 y US 5.2.2.
 */
class ConsultarHistorialExportacionServiceTest {

    @Test
    void debeConsultarYAgruparElHistorialDeUnSolicitante() {
        List<Long> consultados = new ArrayList<>();
        ConsultarHistorialUseCase consulta = id -> {
            consultados.add(id);
            return List.of();
        };
        var servicio = new ConsultarHistorialExportacionService(consulta, id -> "Solicitante " + id);

        List<HistorialSolicitanteExportacion> resultado = servicio.consultar(List.of(10L));

        assertThat(consultados).containsExactly(10L);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).idSolicitante()).isEqualTo(10L);
        assertThat(resultado.get(0).nombreSolicitante()).isEqualTo("Solicitante 10");
        assertThat(resultado.get(0).evaluaciones()).isEmpty();
    }

    @Test
    void debeConsultarYConservarElOrdenDeVariosSolicitantes() {
        List<Long> consultados = new ArrayList<>();
        ConsultarHistorialUseCase consulta = id -> {
            consultados.add(id);
            return List.of();
        };
        var servicio = new ConsultarHistorialExportacionService(consulta, id -> "Solicitante " + id);

        List<HistorialSolicitanteExportacion> resultado =
                servicio.consultar(List.of(10L, 20L, 30L));

        assertThat(consultados).containsExactly(10L, 20L, 30L);
        assertThat(resultado)
                .extracting(HistorialSolicitanteExportacion::idSolicitante)
                .containsExactly(10L, 20L, 30L);
        assertThat(resultado).allSatisfy(historial -> {
            assertThat(historial.nombreSolicitante()).isEqualTo("Solicitante " + historial.idSolicitante());
            assertThat(historial.evaluaciones()).isEmpty();
        });
    }

    @Test
    void debeRechazarUnaListaNula() {
        var servicio = new ConsultarHistorialExportacionService(id -> List.of(), id -> "Solicitante " + id);

        assertThatThrownBy(() -> servicio.consultar(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debe seleccionar al menos un solicitante");
    }

    @Test
    void debeRechazarUnaListaVacia() {
        var servicio = new ConsultarHistorialExportacionService(id -> List.of(), id -> "Solicitante " + id);

        assertThatThrownBy(() -> servicio.consultar(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debe seleccionar al menos un solicitante");
    }

    @Test
    void debeRechazarMasDeCincuentaSolicitantes() {
        var servicio = new ConsultarHistorialExportacionService(id -> List.of(), id -> "Solicitante " + id);
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 51; id++) {
            ids.add(id);
        }

        assertThatThrownBy(() -> servicio.consultar(ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se pueden exportar más de 50 solicitantes en una solicitud");
    }

    @Test
    void debeRechazarIdentificadoresNulos() {
        var servicio = new ConsultarHistorialExportacionService(id -> List.of(), id -> "Solicitante " + id);

        assertThatThrownBy(() -> servicio.consultar(Arrays.asList(10L, null)))
                .isInstanceOf(IdentificadorInvalidoException.class)
                .hasMessageContaining("solicitanteIds");
    }

    @Test
    void debeRechazarIdentificadoresCeroONegativos() {
        var servicio = new ConsultarHistorialExportacionService(id -> List.of(), id -> "Solicitante " + id);

        assertThatThrownBy(() -> servicio.consultar(List.of(0L)))
                .isInstanceOf(IdentificadorInvalidoException.class);
        assertThatThrownBy(() -> servicio.consultar(List.of(-1L)))
                .isInstanceOf(IdentificadorInvalidoException.class);
    }

    @Test
    void debeRechazarIdentificadoresDuplicados() {
        var servicio = new ConsultarHistorialExportacionService(id -> List.of(), id -> "Solicitante " + id);

        assertThatThrownBy(() -> servicio.consultar(List.of(10L, 20L, 10L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se permiten identificadores de solicitantes duplicados");
    }

    @Test
    void debePropagarElErrorCuandoUnSolicitanteNoExiste() {
        ConsultarHistorialUseCase consulta = id -> {
            if (id == 20L) {
                throw new SolicitanteNoEncontradoException();
            }
            return List.of();
        };
        var servicio = new ConsultarHistorialExportacionService(consulta, id -> "Solicitante " + id);

        assertThatThrownBy(() -> servicio.consultar(List.of(10L, 20L, 30L)))
                .isInstanceOf(SolicitanteNoEncontradoException.class);
    }
}
