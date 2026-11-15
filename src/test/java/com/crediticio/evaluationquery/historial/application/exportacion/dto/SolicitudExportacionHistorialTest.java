package com.crediticio.evaluationquery.historial.application.exportacion.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SolicitudExportacionHistorialTest {

    @Test
    void debeConservarLosIdentificadoresSeleccionados() {
        List<Long> ids = List.of(10L, 20L, 30L);

        SolicitudExportacionHistorial solicitud =
                new SolicitudExportacionHistorial(ids);

        assertThat(solicitud.solicitanteIds()).containsExactly(10L, 20L, 30L);
    }
}
