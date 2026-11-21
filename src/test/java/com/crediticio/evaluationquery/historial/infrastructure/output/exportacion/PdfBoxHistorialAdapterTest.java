package com.crediticio.evaluationquery.historial.infrastructure.output.exportacion;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.IOException;
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
                new HistorialSolicitanteExportacion(101L, "Solicitante 101", List.of(evaluacion(9001L))),
                new HistorialSolicitanteExportacion(202L, "Solicitante 202", List.of()));

        byte[] archivo = adapter.generar(historial);

        assertThat(archivo).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        try (var documento = Loader.loadPDF(archivo)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Historial de evaluaciones", "CREDIT-COMPASS",
                    "Solicitante 101", "ID: 101", "Evaluación #9001", "Fecha: 16/11/2026 10:30",
                    "Puntaje total: 720", "Origen: CALCULADO", "Clasificada: Sí",
                    "Categoría: BAJO_RIESGO", "Versión de política: 3", "Estado: COMPLETADA",
                    "Ruta de explicación:", "Ruta de recomendaciones:",
                    "Solicitante 202", "Este solicitante no tiene evaluaciones registradas.");
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
        var historiales = List.of(new HistorialSolicitanteExportacion(303L, "Solicitante 303", evaluaciones));

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
    void debeConservarElContenidoDeUnaRutaExtensaEnElPdf() throws Exception {
        String rutaLarga = "ruta-" + "detalle/".repeat(700);
        var evaluacion = new EvaluacionHistorialResponse(
                9401L, LocalDateTime.of(2026, 11, 16, 10, 30),
                650, "CALCULADO", true, "RIESGO_MEDIO", 4, "COMPLETADA",
                rutaLarga, "/recomendaciones/9401");
        var historiales = List.of(new HistorialSolicitanteExportacion(404L, "Solicitante 404L", List.of(evaluacion)));

        byte[] archivo = adapter.generar(historiales);

        try (var documento = Loader.loadPDF(archivo)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(documento))
                    .contains("Evaluación #9401", "Ruta de explicación:", rutaLarga);
        }
    }

    @Test
    void debeRepetirElEncabezadoVisualAlCrearPaginasAdicionales() throws Exception {
        var historiales = IntStream.rangeClosed(1, 30)
                .mapToObj(id -> new HistorialSolicitanteExportacion(id, "Solicitante " + id, List.of()))
                .toList();

        byte[] archivo = adapter.generar(historiales);

        try (var documento = Loader.loadPDF(archivo)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(documento.getNumberOfPages()).isGreaterThan(1);
            assertThat(texto).contains("CREDIT-COMPASS  |  Historial de evaluaciones");
            assertThat(texto).contains("Solicitante 30");
        }
    }


    @Test
    void debePropagarErrorCuandoFallaElGuardadoDelPdf() {
        var adaptador = new PdfBoxHistorialAdapter(
                (documento, salida) -> { throw new IOException("fallo simulado"); },
                PDPageContentStream::new);

        assertThatThrownBy(() -> adaptador.generar(
                List.of(new HistorialSolicitanteExportacion(101L, "Solicitante 101L", List.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No fue posible generar el reporte PDF")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void debePropagarErrorCuandoFallaLaCreacionDeUnaPagina() {
        var adaptador = new PdfBoxHistorialAdapter(
                PDDocument::save,
                (documento, pagina) -> { throw new IOException("fallo de pagina simulado"); });

        assertThatThrownBy(() -> adaptador.generar(
                List.of(new HistorialSolicitanteExportacion(101L, "Solicitante 101L", List.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No fue posible preparar una página del reporte")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void debeSanitizarValoresNulosYCaracteresNoAdmitidos() throws Exception {
        try (var documento = new PDDocument()) {
            var escritor = new PdfBoxHistorialAdapter.EscritorPdf(documento, PDPageContentStream::new);
            assertThat(escritor.sanitizar(null)).isEqualTo("No disponible");
            assertThat(escritor.sanitizar("texto\ncon\u0001 control")).isEqualTo("texto con  control");
            assertThat(escritor.sanitizar("riesgo 😀")).isEqualTo("riesgo ?");
        }
    }

    @Test
    void debeProcesarTextoVacioYUsarAmbasVariantesDeFuente() throws Exception {
        try (var documento = new PDDocument()) {
            var escritor = new PdfBoxHistorialAdapter.EscritorPdf(documento, PDPageContentStream::new);
            escritor.linea("", false, 9, Color.BLACK);
            escritor.linea("Texto normal", false, 9, Color.BLACK);
            escritor.linea("Texto destacado", true, 9, Color.BLACK);
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void debeCrearUnaNuevaPaginaAlAgotarseElEspacioDisponible() throws Exception {
        try (var documento = new PDDocument()) {
            var escritor = new PdfBoxHistorialAdapter.EscritorPdf(documento, PDPageContentStream::new);
            for (int i = 0; i < 55; i++) {
                escritor.linea("Línea de prueba " + i, false, 9, Color.BLACK);
            }
            assertThat(documento.getNumberOfPages()).isGreaterThan(1);
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
