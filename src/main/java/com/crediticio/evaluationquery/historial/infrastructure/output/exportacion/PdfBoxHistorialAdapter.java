package com.crediticio.evaluationquery.historial.infrastructure.output.exportacion;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorPdfPort;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Genera el reporte PDF sin consultar datos: recibe historiales ya preparados por la capa de aplicación. */
@Component
public class PdfBoxHistorialAdapter implements GeneradorPdfPort {

    private static final float MARGEN_IZQUIERDO = 48;
    private static final float MARGEN_SUPERIOR = 790;
    private static final float INTERLINEADO = 16;
    private static final float LIMITE_INFERIOR = 52;
    private static final int ANCHO_TEXTO = 94;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final PDType1Font FUENTE = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FUENTE_NEGRITA =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    @Override
    public byte[] generar(List<HistorialSolicitanteExportacion> historiales) {
        if (historiales == null || historiales.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un historial para generar el PDF");
        }

        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            EscritorPdf escritor = new EscritorPdf(documento);
            escritor.linea("Historial de evaluaciones", true, 18);
            escritor.linea("Reporte generado por Credit-Compass", false, 10);
            escritor.linea(" ", false, 10);

            for (HistorialSolicitanteExportacion historial : historiales) {
                escritor.linea("Solicitante #" + historial.idSolicitante(), true, 13);
                if (historial.evaluaciones().isEmpty()) {
                    escritor.linea("Sin evaluaciones registradas.", false, 10);
                } else {
                    for (EvaluacionHistorialResponse evaluacion : historial.evaluaciones()) {
                        escritor.linea("Evaluación #" + evaluacion.idEvaluacion(), true, 11);
                        escritor.linea("Fecha: " + FORMATO_FECHA.format(evaluacion.fechaEvaluacion()), false, 10);
                        escritor.linea("Puntaje total: " + evaluacion.scoreTotal(), false, 10);
                        escritor.linea("Origen del puntaje: " + evaluacion.origenPuntaje(), false, 10);
                        escritor.linea("Clasificada: " + (evaluacion.clasificada() ? "Sí" : "No"), false, 10);
                        escritor.linea("Categoría: " + valor(evaluacion.categoria()), false, 10);
                        escritor.linea("Versión de política: " + valor(evaluacion.versionPolitica()), false, 10);
                        escritor.linea("Estado: " + valor(evaluacion.estado()), false, 10);
                        escritor.linea("Ruta de explicación: " + valor(evaluacion.explicacion()), false, 10);
                        escritor.linea("Ruta de recomendaciones: " + valor(evaluacion.recomendaciones()), false, 10);
                        escritor.linea(" ", false, 8);
                    }
                }
                escritor.linea(" ", false, 10);
            }

            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException excepcion) {
            throw new IllegalStateException("No fue posible generar el reporte PDF", excepcion);
        }
    }

    private static String valor(Object valor) {
        return valor == null ? "No disponible" : valor.toString();
    }

    private static class EscritorPdf {
        private final PDDocument documento;
        private PDPage pagina;
        private float posicionY;

        private EscritorPdf(PDDocument documento) {
            this.documento = documento;
            nuevaPagina();
        }

        private void linea(String texto, boolean negrita, float tamano) throws IOException {
            String seguro = texto.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ")
                    .replaceAll("[^\\u0020-\\u00FF]", "?");
            for (int inicio = 0; inicio < seguro.length() || inicio == 0; inicio += ANCHO_TEXTO) {
                if (posicionY < LIMITE_INFERIOR) {
                    nuevaPagina();
                }
                int fin = Math.min(inicio + ANCHO_TEXTO, seguro.length());
                String fragmento = seguro.substring(inicio, fin);
                try (PDPageContentStream contenido = new PDPageContentStream(
                        documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    contenido.beginText();
                    contenido.setFont(negrita ? FUENTE_NEGRITA : FUENTE, tamano);
                    contenido.newLineAtOffset(MARGEN_IZQUIERDO, posicionY);
                    contenido.showText(fragmento);
                    contenido.endText();
                }
                posicionY -= INTERLINEADO;
                if (seguro.isEmpty()) {
                    break;
                }
            }
        }

        private void nuevaPagina() {
            pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            posicionY = MARGEN_SUPERIOR;
        }
    }
}
