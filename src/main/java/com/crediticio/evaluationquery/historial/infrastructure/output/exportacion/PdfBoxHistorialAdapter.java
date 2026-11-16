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

        private String sanitizar(String texto) {
            if (texto == null) {
                return "No disponible";
            }
            return texto.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ")
                    .replaceAll("[^\\u0020-\\u00FF]", "?");
        }

        private void campoEn(PDPageContentStream contenido, float x, float y,
                             String etiqueta, String valorTexto) throws IOException {
            String etiquetaLimpia = sanitizar(etiqueta);
            String valorLimpio = sanitizar(valorTexto);
            float tamano = 9f;

            contenido.beginText();
            contenido.setNonStrokingColor(AZUL_OSCURO);
            contenido.setFont(FUENTE_NEGRITA, tamano);
            contenido.newLineAtOffset(x, y);
            contenido.showText(etiquetaLimpia);
            contenido.endText();

            float anchoEtiqueta = (FUENTE_NEGRITA.getStringWidth(etiquetaLimpia) / 1000f) * tamano;

            contenido.beginText();
            contenido.setNonStrokingColor(TEXTO);
            contenido.setFont(FUENTE, tamano);
            contenido.newLineAtOffset(x + anchoEtiqueta + 4, y);
            contenido.showText(valorLimpio);
            contenido.endText();
        }

        private EscritorPdf(PDDocument documento) {
            this.documento = documento;
            nuevaPagina(true); // Primera página sin encabezado repetido
            
        }

        private void titulo() throws IOException {
            try (PDPageContentStream contenido = new PDPageContentStream(
                    documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
                contenido.beginText();
                contenido.setNonStrokingColor(Color.WHITE);
                contenido.setFont(FUENTE_NEGRITA, 21);
                contenido.newLineAtOffset(MARGEN_IZQUIERDO, 796);
                contenido.showText("Historial de evaluaciones");
                contenido.endText();

                contenido.beginText();
                contenido.setNonStrokingColor(new Color(220, 232, 242));
                contenido.setFont(FUENTE, 10);
                contenido.newLineAtOffset(MARGEN_IZQUIERDO, 774);
                contenido.showText("CREDIT-COMPASS  |  Reporte de evaluaciones");
                contenido.endText();
            }
            posicionY = 710;
        }

        private void seccionSolicitante(long idSolicitante) throws IOException {
            // Añadir separación superior si no estamos al inicio de la página
            if (posicionY < 700) {
                posicionY -= 16; 
            }
            asegurarEspacio(46);
            try (PDPageContentStream contenido = new PDPageContentStream(
                    documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
                contenido.setNonStrokingColor(AZUL_CLARO);
                contenido.addRect(MARGEN_IZQUIERDO, posicionY - 28, ANCHO_PAGINA - 84, 28);
                contenido.fill();
                contenido.setNonStrokingColor(ACENTO);
                contenido.addRect(MARGEN_IZQUIERDO, posicionY - 28, 4, 28);
                contenido.fill();
                contenido.beginText();
                contenido.setNonStrokingColor(AZUL_OSCURO);
                contenido.setFont(FUENTE_NEGRITA, 12);
                contenido.newLineAtOffset(MARGEN_IZQUIERDO + 13, posicionY - 18);
                contenido.showText("SOLICITANTE  #" + idSolicitante);
                contenido.endText();
            }
            // Dejar espacio debajo de la barra del solicitante antes de la tarjeta
            posicionY -= 38; 
        }

        private void tarjetaEvaluacion(EvaluacionHistorialResponse evaluacion) throws IOException {
            float altoTarjeta = 112;
            asegurarEspacio(altoTarjeta + 12);

            float techoTarjeta = posicionY;
            float baseTarjeta = techoTarjeta - altoTarjeta;
            float anchoTarjeta = ANCHO_PAGINA - (MARGEN_IZQUIERDO * 2);
            
            float colIzq = MARGEN_IZQUIERDO + 12;
            // Posición fija para la columna derecha: alinea Fecha, Origen, Categoría y Estado
            float colDer = (MARGEN_IZQUIERDO + anchoTarjeta - 12) - 145;

            try (PDPageContentStream contenido = new PDPageContentStream(
                    documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {

                // Fondo y borde de la tarjeta
                contenido.setNonStrokingColor(FONDO_TARJETA);
                contenido.addRect(MARGEN_IZQUIERDO, baseTarjeta, anchoTarjeta, altoTarjeta);
                contenido.fill();

                contenido.setStrokingColor(BORDE);
                contenido.addRect(MARGEN_IZQUIERDO, baseTarjeta, anchoTarjeta, altoTarjeta);
                contenido.stroke();

                // Barra de acento izquierda
                contenido.setNonStrokingColor(ACENTO);
                contenido.addRect(MARGEN_IZQUIERDO, baseTarjeta, 3, altoTarjeta);
                contenido.fill();

                // Línea divisoria de cabecera
                contenido.setStrokingColor(BORDE);
                contenido.moveTo(colIzq, techoTarjeta - 26);
                contenido.lineTo(MARGEN_IZQUIERDO + anchoTarjeta - 12, techoTarjeta - 26);
                contenido.stroke();

                // --- CABECERA: Título a la izquierda y Fecha alineada con la columna derecha ---
                float yCabecera = techoTarjeta - 18;
                contenido.beginText();
                contenido.setNonStrokingColor(AZUL_OSCURO);
                contenido.setFont(FUENTE_NEGRITA, 11);
                contenido.newLineAtOffset(colIzq, yCabecera);
                contenido.showText("Evaluación #" + evaluacion.idEvaluacion());
                contenido.endText();

                String fechaTexto = FORMATO_FECHA.format(evaluacion.fechaEvaluacion());
                campoEn(contenido, colDer, yCabecera, "Fecha:", fechaTexto);

                // --- CUERPO EN 2 COLUMNAS PERFECTAMENTE ALINEADAS ---
                float yFila = techoTarjeta - 41;

                // Fila 1
                campoEn(contenido, colIzq, yFila, "Puntaje total:", String.valueOf(evaluacion.scoreTotal()));
                campoEn(contenido, colDer, yFila, "Origen:", valor(evaluacion.origenPuntaje()));
                yFila -= 15;

                // Fila 2
                campoEn(contenido, colIzq, yFila, "Clasificada:", evaluacion.clasificada() ? "Sí" : "No");
                campoEn(contenido, colDer, yFila, "Categoría:", valor(evaluacion.categoria()));
                yFila -= 15;

                // Fila 3
                campoEn(contenido, colIzq, yFila, "Versión de política:", valor(evaluacion.versionPolitica()));
                campoEn(contenido, colDer, yFila, "Estado:", valor(evaluacion.estado()));
                yFila -= 17;

                // --- RUTAS (ANCHO COMPLETO) ---
                campoEn(contenido, colIzq, yFila, "Ruta de explicación:", valor(evaluacion.explicacion()));
                yFila -= 14;
                campoEn(contenido, colIzq, yFila, "Ruta de recomendaciones:", valor(evaluacion.recomendaciones()));
            }

            posicionY = baseTarjeta - 12;
        }
                
        private void mensaje(String texto) throws IOException {
            asegurarEspacio(32);
            posicionY -= 4;
            linea(texto, false, 9, TEXTO);
            posicionY -= 6;
        }

        private void linea(String texto, boolean negrita, float tamano, Color color) throws IOException {
            String seguro = texto.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ")
                    .replaceAll("[^\\u0020-\\u00FF]", "?");
            for (int inicio = 0; inicio < seguro.length() || inicio == 0; inicio += ANCHO_TEXTO) {
                if (posicionY < LIMITE_INFERIOR) {
                    nuevaPagina(false);
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
                nuevaPagina(false);
            }
        }

        private void nuevaPagina(boolean esPrimeraPagina) {
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

                if (!esPrimeraPagina) {
                    contenido.beginText();
                    contenido.setNonStrokingColor(Color.WHITE);
                    contenido.setFont(FUENTE_NEGRITA, 12);
                    contenido.newLineAtOffset(MARGEN_IZQUIERDO, 790);
                    contenido.showText("CREDIT-COMPASS  |  Historial de evaluaciones");
                    contenido.endText();
                }
            } catch (IOException excepcion) {
                throw new IllegalStateException("No fue posible preparar una página del reporte", excepcion);
            }
        }
    }
}
