package com.crediticio.evaluationquery.historial.infrastructure.output.exportacion;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

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
            assertThat(texto).contains("Historial de evaluaciones", "Solicitante #101",
                    "Evaluación #9001", "Puntaje total: 720", "Solicitante #202",
                    "Sin evaluaciones registradas.");
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
