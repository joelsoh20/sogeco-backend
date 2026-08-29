package com.sogeco.fleet.common.export;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ecriture XLSX generique via Apache POI.
 *
 * Une seule implementation pour les quatre types de rapport : chaque
 * service ne fournit que ses en-tetes et ses lignes, jamais de logique
 * de mise en forme.
 */
public final class XlsxWriter {

    private XlsxWriter() {
    }

    public static byte[] write(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    writeCell(row.createCell(c), values.get(c), dateStyle);
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            workbook.write(buffer);
            return buffer.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Echec de generation du fichier Excel", e);
        }
    }

    private static void writeCell(Cell cell, Object value, CellStyle dateStyle) {
        switch (value) {
            case null -> cell.setBlank();
            case BigDecimal decimal -> cell.setCellValue(decimal.doubleValue());
            case Number number -> cell.setCellValue(number.doubleValue());
            case LocalDate date -> {
                cell.setCellValue(date);
                cell.setCellStyle(dateStyle);
            }
            default -> cell.setCellValue(value.toString());
        }
    }
}
