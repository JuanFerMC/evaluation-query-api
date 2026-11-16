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

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Genera reportes PDF con una presentación visual agrupada por solicitante. */
@Component
public class PdfBoxHistorialAdapter implements GeneradorPdfPort {

    private static final float MARGEN_IZQUIERDO = 42;
    private static final float LIMITE_INFERIOR = 48;
    private static final float INTERLINEADO = 13;
    private static final int ANCHO_TEXTO = 88;
    private static final float ANCHO_PAGINA = PDRectangle.A4.getWidth();
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color AZUL_OSCURO = new Color(24, 40, 70);
    private static final Color AZUL_CLARO = new Color(232, 241, 249);
    private static final Color FONDO_TARJETA = new Color(248, 250, 252);
    private static final Color BORDE = new Color(220, 228, 236);
    private static final Color TEXTO = new Color(50, 60, 70);
    private static final Color ACENTO = new Color(25, 175, 205);

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
            escritor.titulo();
            escritor.linea("", false, 9, TEXTO);

            for (HistorialSolicitanteExportacion historial : historiales) {
                escritor.seccionSolicitante(historial.idSolicitante());
                if (historial.evaluaciones().isEmpty()) {
                    escritor.mensaje("Este solicitante no tiene evaluaciones registradas.");
                } else {
                    for (EvaluacionHistorialResponse evaluacion : historial.evaluaciones()) {
                        escritor.tarjetaEvaluacion(evaluacion);
                    }
                }
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

        private void titulo() throws IOException {
            try (PDPageContentStream contenido = new PDPageContentStream(
                    documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
                contenido.beginText();
                contenido.setNonStrokingColor(Color.WHITE);
                contenido.setFont(FUENTE_NEGRITA, 21);
                contenido.newLineAtOffset(MARGEN_IZQUIERDO, 800);
                contenido.showText("Historial de evaluaciones");
                contenido.endText();

                contenido.beginText();
                contenido.setNonStrokingColor(new Color(220, 232, 242));
                contenido.setFont(FUENTE, 10);
                contenido.newLineAtOffset(MARGEN_IZQUIERDO, 780);
                contenido.showText("CREDIT-COMPASS  |  Reporte de evaluaciones");
                contenido.endText();
            }
            posicionY = 716;
        }

        private void seccionSolicitante(long idSolicitante) throws IOException {
            asegurarEspacio(42);
            try (PDPageContentStream contenido = new PDPageContentStream(
                    documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
                contenido.setNonStrokingColor(AZUL_CLARO);
                contenido.addRect(MARGEN_IZQUIERDO, posicionY - 27, ANCHO_PAGINA - 84, 28);
                contenido.fill();
                contenido.setNonStrokingColor(ACENTO);
                contenido.addRect(MARGEN_IZQUIERDO, posicionY - 27, 4, 28);
                contenido.fill();
                contenido.beginText();
                contenido.setNonStrokingColor(AZUL_OSCURO);
                contenido.setFont(FUENTE_NEGRITA, 12);
                contenido.newLineAtOffset(MARGEN_IZQUIERDO + 13, posicionY - 18);
                contenido.showText("SOLICITANTE  #" + idSolicitante);
                contenido.endText();
            }
            posicionY -= 42;
        }

        private void tarjetaEvaluacion(EvaluacionHistorialResponse evaluacion) throws IOException {
            asegurarEspacio(174);
            try (PDPageContentStream contenido = new PDPageContentStream(
                    documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
                contenido.setNonStrokingColor(FONDO_TARJETA);
                contenido.addRect(MARGEN_IZQUIERDO, posicionY - 158, ANCHO_PAGINA - 84, 158);
                contenido.fill();
                contenido.setStrokingColor(BORDE);
                contenido.addRect(MARGEN_IZQUIERDO, posicionY - 158, ANCHO_PAGINA - 84, 158);
                contenido.stroke();
                contenido.setNonStrokingColor(ACENTO);
                contenido.addRect(MARGEN_IZQUIERDO, posicionY - 158, 3, 158);
                contenido.fill();
            }

            linea("Evaluación #" + evaluacion.idEvaluacion(), true, 11, AZUL_OSCURO);
            linea("Fecha: " + FORMATO_FECHA.format(evaluacion.fechaEvaluacion()), false, 9, TEXTO);
            linea("Puntaje total: " + evaluacion.scoreTotal()
                    + "    |    Origen: " + valor(evaluacion.origenPuntaje()), false, 9, TEXTO);
            linea("Clasificada: " + (evaluacion.clasificada() ? "Sí" : "No")
                    + "    |    Categoría: " + valor(evaluacion.categoria()), false, 9, TEXTO);
            linea("Versión de política: " + valor(evaluacion.versionPolitica())
                    + "    |    Estado: " + valor(evaluacion.estado()), false, 9, TEXTO);
            linea("Ruta de explicación: " + valor(evaluacion.explicacion()), false, 9, TEXTO);
            linea("Ruta de recomendaciones: " + valor(evaluacion.recomendaciones()), false, 9, TEXTO);
            posicionY -= 13;
        }

        private void mensaje(String texto) throws IOException {
            asegurarEspacio(35);
            linea(texto, false, 9, TEXTO);
            posicionY -= 8;
        }

        private void linea(String texto, boolean negrita, float tamano, Color color) throws IOException {
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
                    contenido.setNonStrokingColor(color);
                    contenido.setFont(negrita ? FUENTE_NEGRITA : FUENTE, tamano);
                    contenido.newLineAtOffset(MARGEN_IZQUIERDO + 12, posicionY);
                    contenido.showText(fragmento);
                    contenido.endText();
                }
                posicionY -= INTERLINEADO;
                if (seguro.isEmpty()) {
                    break;
                }
            }
        }

        private void asegurarEspacio(float requerido) {
            if (posicionY - requerido < LIMITE_INFERIOR) {
                nuevaPagina();
            }
        }

        private void nuevaPagina() {
            pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            posicionY = 716;
            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.setNonStrokingColor(AZUL_OSCURO);
                contenido.addRect(0, 742, ANCHO_PAGINA, 100);
                contenido.fill();
                contenido.setNonStrokingColor(ACENTO);
                contenido.addRect(0, 738, ANCHO_PAGINA, 4);
                contenido.fill();
                contenido.beginText();
                contenido.setNonStrokingColor(Color.WHITE);
                contenido.setFont(FUENTE_NEGRITA, 12);
                contenido.newLineAtOffset(MARGEN_IZQUIERDO, 790);
                contenido.showText("CREDIT-COMPASS  |  Historial de evaluaciones");
                contenido.endText();
            } catch (IOException excepcion) {
                throw new IllegalStateException("No fue posible preparar una página del reporte", excepcion);
            }
        }
    }
}
