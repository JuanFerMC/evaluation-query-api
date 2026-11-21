package com.crediticio.evaluationquery.historial.infrastructure.output.exportacion;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApachePoiHistorialAdapterTest {

    private final ApachePoiHistorialAdapter adapter = new ApachePoiHistorialAdapter();

    @Test
    void debeGenerarExcelConSeccionesPorSolicitanteYDatosDeEvaluacion() throws Exception {
        var historiales = List.of(
                new HistorialSolicitanteExportacion(101L, "Solicitante 101", List.of(evaluacion(9001L))),
                new HistorialSolicitanteExportacion(202L, "Solicitante 202", List.of()));

        byte[] archivo = adapter.generar(historiales);

        assertThat(archivo).isNotEmpty();
        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(archivo))) {
            var hoja = libro.getSheet("Historial de evaluaciones");
            assertThat(hoja).isNotNull();
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Solicitante 101 | ID: 101");
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("ID evaluación");
            assertThat(hoja.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(9001);
            assertThat(hoja.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(720);
            assertThat(hoja.getRow(2).getCell(3).getStringCellValue()).isEqualTo("CALCULADO");
            assertThat(hoja.getRow(2).getCell(4).getStringCellValue()).isEqualTo("Sí");
            assertThat(hoja.getRow(2).getCell(5).getStringCellValue()).isEqualTo("BAJO_RIESGO");
            assertThat(hoja.getRow(2).getCell(6).getStringCellValue()).isEqualTo("3");
            assertThat(hoja.getRow(2).getCell(7).getStringCellValue()).isEqualTo("COMPLETADA");
            assertThat(hoja.getRow(2).getCell(8).getStringCellValue())
                    .isEqualTo("/evaluaciones/9001/explicacion");
            assertThat(hoja.getRow(2).getCell(9).getStringCellValue())
                    .isEqualTo("/evaluaciones/9001/recomendaciones");
            assertThat(hoja.getPaneInformation().isFreezePane()).isTrue();
            assertThat(hoja.getColumnWidth(8)).isEqualTo(52 * 256);
            assertThat(hoja.getRow(1).getCell(0).getCellStyle().getFillForegroundColor())
                    .isNotEqualTo((short) 0);
            assertThat(hoja.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Solicitante 202 | ID: 202");
            assertThat(hoja.getRow(6).getCell(0).getStringCellValue())
                    .isEqualTo("Sin evaluaciones registradas.");
        }
    }

    @Test
    void debeRechazarUnaListaDeHistorialesVacia() {
        assertThatThrownBy(() -> adapter.generar(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarUnaListaDeHistorialesNula() {
        assertThatThrownBy(() -> adapter.generar(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRepresentarLosCamposOpcionalesNulosYLaClasificacionNegativa() throws Exception {
        var historial = List.of(new HistorialSolicitanteExportacion(
                303L, "Solicitante 303", List.of(evaluacionConDatosOpcionales(9301L))));

        byte[] archivo = adapter.generar(historial);

        try (var libro = WorkbookFactory.create(new ByteArrayInputStream(archivo))) {
            var hoja = libro.getSheet("Historial de evaluaciones");
            var fila = hoja.getRow(2);

            assertThat(fila.getCell(4).getStringCellValue()).isEqualTo("No");
            assertThat(fila.getCell(5).getStringCellValue()).isEqualTo("No disponible");
            assertThat(fila.getCell(6).getStringCellValue()).isEqualTo("No disponible");
            assertThat(fila.getCell(7).getStringCellValue()).isEqualTo("No disponible");
            assertThat(fila.getCell(8).getStringCellValue()).isEqualTo("No disponible");
            assertThat(fila.getCell(9).getStringCellValue()).isEqualTo("No disponible");
        }
    }


    @Test
    void debePropagarErrorCuandoFallaLaEscrituraDelLibroExcel() {
        var adaptador = new ApachePoiHistorialAdapter(
                (libro, salida) -> { throw new IOException("fallo simulado"); });

        assertThatThrownBy(() -> adaptador.generar(
                List.of(new HistorialSolicitanteExportacion(101L, "Solicitante 101", List.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No fue posible generar el reporte Excel")
                .hasCauseInstanceOf(IOException.class);
    }

    private static EvaluacionHistorialResponse evaluacion(long id) {
        return new EvaluacionHistorialResponse(id, LocalDateTime.of(2026, 11, 16, 10, 30),
                720, "CALCULADO", true, "BAJO_RIESGO", 3, "COMPLETADA",
                "/evaluaciones/9001/explicacion", "/evaluaciones/9001/recomendaciones");
    }

    private static EvaluacionHistorialResponse evaluacionConDatosOpcionales(long id) {
        return new EvaluacionHistorialResponse(id, LocalDateTime.of(2026, 11, 16, 10, 30),
                0, "DERIVADO_DE_DETALLES", false, null, null, null, null, null);
    }
}
