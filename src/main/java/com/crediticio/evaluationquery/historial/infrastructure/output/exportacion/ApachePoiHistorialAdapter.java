package com.crediticio.evaluationquery.historial.infrastructure.output.exportacion;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;
import com.crediticio.evaluationquery.historial.application.exportacion.dto.HistorialSolicitanteExportacion;
import com.crediticio.evaluationquery.historial.ports.output.exportacion.GeneradorExcelPort;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Genera un libro XLSX con una sección independiente para cada solicitante. */
@Component
public class ApachePoiHistorialAdapter implements GeneradorExcelPort {

    @FunctionalInterface
    interface WorkbookSaver {
        void guardar(XSSFWorkbook libro, OutputStream salida) throws IOException;
    }

    private final WorkbookSaver workbookSaver;

    public ApachePoiHistorialAdapter() {
        this((libro, salida) -> libro.write(salida));
    }

    ApachePoiHistorialAdapter(WorkbookSaver workbookSaver) {
        this.workbookSaver = workbookSaver;
    }

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] ENCABEZADOS = {
            "ID evaluación", "Fecha", "Puntaje total", "Origen del puntaje", "Clasificada",
            "Categoría", "Versión de política", "Estado", "Ruta de explicación", "Ruta de recomendaciones"
    };

    @Override
    public byte[] generar(List<HistorialSolicitanteExportacion> historiales) {
        if (historiales == null || historiales.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un historial para generar el Excel");
        }

        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Historial de evaluaciones");
            CellStyle estiloSolicitante = crearEstiloSolicitante(libro);
            CellStyle estiloEncabezado = crearEstiloEncabezado(libro);
            int numeroFila = 0;

            for (HistorialSolicitanteExportacion historial : historiales) {
                Row filaSolicitante = hoja.createRow(numeroFila++);
                Cell celdaSolicitante = filaSolicitante.createCell(0);
                celdaSolicitante.setCellValue(historial.nombreSolicitante() + " | ID: " + historial.idSolicitante());
                celdaSolicitante.setCellStyle(estiloSolicitante);
                hoja.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                        filaSolicitante.getRowNum(), filaSolicitante.getRowNum(), 0, ENCABEZADOS.length - 1));

                Row filaEncabezados = hoja.createRow(numeroFila++);
                for (int columna = 0; columna < ENCABEZADOS.length; columna++) {
                    Cell celda = filaEncabezados.createCell(columna);
                    celda.setCellValue(ENCABEZADOS[columna]);
                    celda.setCellStyle(estiloEncabezado);
                }

                if (historial.evaluaciones().isEmpty()) {
                    Row filaVacia = hoja.createRow(numeroFila++);
                    filaVacia.createCell(0).setCellValue("Sin evaluaciones registradas.");
                } else {
                    for (EvaluacionHistorialResponse evaluacion : historial.evaluaciones()) {
                        Row fila = hoja.createRow(numeroFila++);
                        escribirEvaluacion(fila, evaluacion);
                    }
                }
                numeroFila++;
            }

            hoja.createFreezePane(0, 2);
            for (int columna = 0; columna < ENCABEZADOS.length; columna++) {
                hoja.setColumnWidth(columna, columna == 8 || columna == 9 ? 52 * 256 : 22 * 256);
            }
            workbookSaver.guardar(libro, salida);
            return salida.toByteArray();
        } catch (IOException excepcion) {
            throw new IllegalStateException("No fue posible generar el reporte Excel", excepcion);
        }
    }

    private static void escribirEvaluacion(Row fila, EvaluacionHistorialResponse evaluacion) {
        int columna = 0;
        fila.createCell(columna++).setCellValue(evaluacion.idEvaluacion());
        fila.createCell(columna++).setCellValue(FORMATO_FECHA.format(evaluacion.fechaEvaluacion()));
        fila.createCell(columna++).setCellValue(evaluacion.scoreTotal());
        fila.createCell(columna++).setCellValue(evaluacion.origenPuntaje());
        fila.createCell(columna++).setCellValue(evaluacion.clasificada() ? "Sí" : "No");
        fila.createCell(columna++).setCellValue(valor(evaluacion.categoria()));
        fila.createCell(columna++).setCellValue(evaluacion.versionPolitica() == null
                ? "No disponible" : evaluacion.versionPolitica().toString());
        fila.createCell(columna++).setCellValue(valor(evaluacion.estado()));
        fila.createCell(columna++).setCellValue(valor(evaluacion.explicacion()));
        fila.createCell(columna).setCellValue(valor(evaluacion.recomendaciones()));
    }

    private static String valor(String valor) {
        return valor == null ? "No disponible" : valor;
    }

    private static CellStyle crearEstiloSolicitante(XSSFWorkbook libro) {
        CellStyle estilo = libro.createCellStyle();
        Font fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setFontHeightInPoints((short) 12);
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private static CellStyle crearEstiloEncabezado(XSSFWorkbook libro) {
        CellStyle estilo = libro.createCellStyle();
        Font fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setWrapText(true);
        return estilo;
    }
}
