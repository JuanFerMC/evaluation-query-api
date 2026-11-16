package com.crediticio.evaluationquery.historial.infrastructure.output.exportacion;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApachePoiHistorialAdapterTest {

    private final ApachePoiHistorialAdapter adapter = new ApachePoiHistorialAdapter();

    @Test
    void debeGenerarExcelConSeccionesPorSolicitanteYDatosDeEvaluacion() throws Exception {
        var historiales = List.of(
                new HistorialSolicitanteExportacion(101L, List.of(evaluacion(9001L))),
                new HistorialSolicitanteExportacion(202L, List.of()));

        byte[] archivo = adapter.generar(historiales);

        assertThat(archivo).isNotEmpty();
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(archivo))) {
            var hoja = libro.getSheet("Historial de evaluaciones");
            assertThat(hoja).isNotNull();
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Solicitante #101");
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("ID evaluación");
            assertThat(hoja.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(9001);
            assertThat(hoja.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(720);
            assertThat(hoja.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Solicitante #202");
            assertThat(hoja.getRow(7).getCell(0).getStringCellValue())
                    .isEqualTo("Sin evaluaciones registradas.");
        }
    }

    @Test
    void debeRechazarUnaListaDeHistorialesVacia() {
        assertThatThrownBy(() -> adapter.generar(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EvaluacionHistorialResponse evaluacion(long id) {
        return new EvaluacionHistorialResponse(id, LocalDateTime.of(2026, 11, 16, 10, 30),
                720, "CALCULADO", true, "BAJO_RIESGO", 3, "COMPLETADA",
                "/evaluaciones/9001/explicacion", "/evaluaciones/9001/recomendaciones");
    }
}
