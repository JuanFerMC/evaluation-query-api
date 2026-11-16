package com.crediticio.evaluationquery.historial.infrastructure.output.exportacion;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfBoxHistorialAdapterTest {

    private final PdfBoxHistorialAdapter adapter = new PdfBoxHistorialAdapter();

    @Test
    void debeGenerarPdfConEvaluacionesAgrupadasPorSolicitante() throws Exception {
        var historial = List.of(
                new HistorialSolicitanteExportacion(101L, List.of(evaluacion(9001L))),
                new HistorialSolicitanteExportacion(202L, List.of()));

        byte[] archivo = adapter.generar(historial);

        assertThat(archivo).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        try (var documento = Loader.loadPDF(archivo)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Historial de evaluaciones", "CREDIT-COMPASS",
                    "SOLICITANTE  #101", "Evaluación #9001", "Puntaje total: 720",
                    "SOLICITANTE  #202", "Este solicitante no tiene evaluaciones registradas.");
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
    void debeRepresentarValoresNulosYGenerarVariasPaginas() throws Exception {
        var evaluaciones = IntStream.rangeClosed(1, 8)
                .mapToObj(id -> evaluacionConDatosOpcionales(9100L + id))
                .toList();
        var historiales = List.of(new HistorialSolicitanteExportacion(303L, evaluaciones));

        byte[] archivo = adapter.generar(historiales);

        try (var documento = Loader.loadPDF(archivo)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(documento.getNumberOfPages()).isGreaterThan(1);
            assertThat(texto).contains("Clasificada: No", "Categoría: No disponible",
                    "Versión de política: No disponible", "Estado: No disponible",
                    "Ruta de explicación: No disponible", "Ruta de recomendaciones: No disponible");
        }
    }

    @Test
    void debeDividirTextosExtensosYContinuarEnUnaNuevaPagina() throws Exception {
        var evaluacion = new EvaluacionHistorialResponse(
                9401L, LocalDateTime.of(2026, 11, 16, 10, 30),
                650, "CALCULADO", true, "RIESGO_MEDIO", 4, "COMPLETADA",
                "ruta-" + "detalle/".repeat(700), "/recomendaciones/9401");
        var historiales = List.of(new HistorialSolicitanteExportacion(404L, List.of(evaluacion)));

        byte[] archivo = adapter.generar(historiales);

        try (var documento = Loader.loadPDF(archivo)) {
            assertThat(documento.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(documento)).contains("Evaluación #9401");
        }
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
