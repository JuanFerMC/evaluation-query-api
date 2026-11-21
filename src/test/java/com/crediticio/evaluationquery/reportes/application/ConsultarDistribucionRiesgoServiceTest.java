package com.crediticio.evaluationquery.reportes.application;

import com.crediticio.evaluationquery.reportes.application.dto.DistribucionRiesgoResponse;
import com.crediticio.evaluationquery.reportes.application.dto.NivelRiesgoResponse;
import com.crediticio.evaluationquery.reportes.domain.DistribucionRiesgo;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Map;

import static com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo.ALTO;
import static com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo.BAJO;
import static com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo.MEDIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US 7.1.1 con el puerto simulado: formato de la respuesta y propagación de los errores de consulta. */
class ConsultarDistribucionRiesgoServiceTest {

    @Test
    void debeDevolverLasTresCategoriasEnOrdenConCantidadPorcentajeYTotal() {
        ConsultarDistribucionRiesgoService servicio = new ConsultarDistribucionRiesgoService(
                () -> DistribucionRiesgo.de(Map.of(BAJO, 2L, ALTO, 2L, MEDIO, 1L), 1), new DistribucionRiesgoMapper());

        DistribucionRiesgoResponse respuesta = servicio.consultar();

        assertThat(respuesta.totalEvaluaciones()).isEqualTo(5);
        assertThat(respuesta.evaluacionesSinClasificar()).isEqualTo(1);
        assertThat(respuesta.distribucion()).extracting(NivelRiesgoResponse::categoria)
                .containsExactly("BAJO", "MEDIO", "ALTO");
        assertThat(respuesta.distribucion()).extracting(NivelRiesgoResponse::cantidad).containsExactly(2L, 1L, 2L);
        assertThat(respuesta.distribucion().get(0).porcentaje()).isEqualByComparingTo("40.00");
        assertThat(respuesta.distribucion().get(1).porcentaje()).isEqualByComparingTo("20.00");
        assertThat(respuesta.distribucion().stream().mapToLong(NivelRiesgoResponse::cantidad).sum())
                .isEqualTo(respuesta.totalEvaluaciones());
    }

    @Test
    void unErrorDeConsultaDebePropagarseSinInventarUnaDistribucion() {
        ConsultarDistribucionRiesgoService servicio = new ConsultarDistribucionRiesgoService(() -> {
            throw new DataAccessResourceFailureException("base no disponible");
        }, new DistribucionRiesgoMapper());

        assertThatThrownBy(servicio::consultar).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
