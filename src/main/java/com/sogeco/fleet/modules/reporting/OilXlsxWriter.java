package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.modules.reporting.OilReportService.CityOil;
import com.sogeco.fleet.modules.reporting.OilReportService.OilReport;
import com.sogeco.fleet.modules.reporting.OilReportService.Output;
import com.sogeco.fleet.modules.reporting.OilReportService.Purchase;
import com.sogeco.fleet.modules.reporting.OilReportService.VehicleTotal;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Classeur « Suivi du fut d'huile moteur », mis en page comme la fiche papier de la logistique :
 * titre « Departement Logistique », colonnes DATE / LIBELLE / Qte/L, restant du fut precedent,
 * approvisionnements, TOTAL, une ligne de sortie par camion servi, QUANTITE CONSOMMEE et
 * QUANTITE RESTANTE. Une feuille par ville (le stock est tenu par ville), puis une feuille
 * « Par camion » qui totalise l'huile consommee par camion.
 */
@Component
public class OilXlsxWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FONT = "Cambria";

    public byte[] write(OilReport report) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            List<CityOil> cities = report.cities().isEmpty()
                    ? List.of(new CityOil(null, "", BigDecimal.ZERO, List.of(), List.of()))
                    : report.cities();
            for (CityOil city : cities) {
                writeFollowUp(wb, st, report, city);
            }
            writeByVehicle(wb, st, report, cities);
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Echec de generation du fichier Excel", e);
        }
    }

    // ------------------------------------------------------------------
    // Une feuille par ville : suivi du fut
    // ------------------------------------------------------------------

    private void writeFollowUp(XSSFWorkbook wb, Styles st, OilReport r, CityOil c) {
        String suffix = c.cityName().isEmpty() ? "" : " - " + c.cityName();
        Sheet sheet = wb.createSheet(sheetName("Suivi huile" + suffix));
        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 64 * 256);
        sheet.setColumnWidth(2, 11 * 256);

        int row = 0;
        merged(sheet, row++, "Département Logistique", st.title);
        merged(sheet, row++, "SUIVI DU FUT D'HUILE MOTEUR" + (c.cityName().isEmpty() ? "" : " - " + c.cityName().toUpperCase()), st.subtitle);
        Row dateRow = sheet.createRow(row++);
        text(dateRow, 0, "Période du %s au %s".formatted(DATE.format(r.from()), DATE.format(r.to())), st.plain);
        text(dateRow, 2, DATE.format(LocalDate.now()), st.right);
        row++;

        Row head = sheet.createRow(row++);
        text(head, 0, "DATE", st.header);
        text(head, 1, "LIBELLE", st.header);
        text(head, 2, "Qte/L", st.headerRight);

        int firstQty = row;
        Row opening = sheet.createRow(row++);
        text(opening, 0, "", st.cell);
        text(opening, 1, "RESTANT DU FÛT PRÉCÉDENT", st.cellBold);
        number(opening, 2, c.opening(), st.qtyBold);

        for (Purchase p : c.purchases()) {
            Row line = sheet.createRow(row++);
            text(line, 0, DATE.format(p.date()), st.cell);
            text(line, 1, "APPROVISIONNEMENT EN HUILE" + (p.note() == null ? "" : " - " + p.note().toUpperCase()), st.cell);
            number(line, 2, p.quantity(), st.qtyBold);
        }
        int lastQty = row - 1;

        Row total = sheet.createRow(row++);
        text(total, 0, "", st.cell);
        text(total, 1, "TOTAL", st.cellBoldCenter);
        formula(total, 2, "SUM(C%d:C%d)".formatted(firstQty + 1, lastQty + 1), st.qtyBold);
        int totalRow = row;          // numero Excel (1-base) de la ligne TOTAL
        row++;                       // ligne vide

        int firstOut = row;
        for (Output o : c.outputs()) {
            Row line = sheet.createRow(row++);
            text(line, 0, DATE.format(o.date()), st.cell);
            String who = o.recipient() == null || o.recipient().isBlank() ? "" : "  " + o.recipient().toUpperCase();
            text(line, 1, "SORTIE D'HUILE POUR VEH " + o.registrationNumber().toUpperCase() + who, st.cell);
            number(line, 2, o.quantity(), st.qtyBold);
        }
        int lastOut = row - 1;
        row += 2;

        Row consumed = sheet.createRow(row++);
        text(consumed, 1, "QUANTITE CONSOMMEE", st.cellBold);
        if (c.outputs().isEmpty()) {
            number(consumed, 2, BigDecimal.ZERO, st.qtyBold);
        } else {
            formula(consumed, 2, "SUM(C%d:C%d)".formatted(firstOut + 1, lastOut + 1), st.qtyBold);
        }
        Row remaining = sheet.createRow(row++);
        text(remaining, 1, "QUANTITE RESTANTE", st.cellBold);
        formula(remaining, 2, "C%d-C%d".formatted(totalRow, row - 1), st.qtyBold);
    }

    // ------------------------------------------------------------------
    // Feuille « Par camion »
    // ------------------------------------------------------------------

    private void writeByVehicle(XSSFWorkbook wb, Styles st, OilReport r, List<CityOil> cities) {
        Sheet sheet = wb.createSheet("Par camion");
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 42 * 256);
        sheet.setColumnWidth(3, 11 * 256);
        sheet.setColumnWidth(4, 16 * 256);

        int row = 0;
        text(sheet.createRow(row++), 0, "HUILE MOTEUR CONSOMMÉE PAR CAMION", st.subtitleLeft);
        text(sheet.createRow(row++), 0, "Période du %s au %s".formatted(DATE.format(r.from()), DATE.format(r.to())), st.plain);
        row++;

        Row head = sheet.createRow(row++);
        text(head, 0, "CAMION", st.header);
        text(head, 1, "VILLE", st.header);
        text(head, 2, "CHAUFFEUR(S)", st.header);
        text(head, 3, "SORTIES", st.headerRight);
        text(head, 4, "QUANTITÉ (L)", st.headerRight);

        int first = row;
        for (VehicleTotal v : r.byVehicle()) {
            Row line = sheet.createRow(row++);
            text(line, 0, v.registrationNumber(), st.cell);
            text(line, 1, v.cityName(), st.cell);
            text(line, 2, v.recipients(), st.cell);
            number(line, 3, BigDecimal.valueOf(v.outputCount()), st.qty);
            number(line, 4, v.quantity(), st.qtyBold);
        }
        int last = row - 1;

        Row total = sheet.createRow(row++);
        text(total, 0, "TOTAL CONSOMMÉ", st.cellBold);
        text(total, 1, "", st.cellBold);
        text(total, 2, "", st.cellBold);
        if (r.byVehicle().isEmpty()) {
            number(total, 3, BigDecimal.ZERO, st.qtyBold);
            number(total, 4, BigDecimal.ZERO, st.qtyBold);
        } else {
            formula(total, 3, "SUM(D%d:D%d)".formatted(first + 1, last + 1), st.qtyBold);
            formula(total, 4, "SUM(E%d:E%d)".formatted(first + 1, last + 1), st.qtyBold);
        }
        for (CityOil c : cities) {
            Row remaining = sheet.createRow(row++);
            text(remaining, 0, "RESTE EN STOCK", st.cellBold);
            text(remaining, 1, c.cityName(), st.cellBold);
            text(remaining, 2, "", st.cellBold);
            text(remaining, 3, "", st.cellBold);
            number(remaining, 4, c.remaining(), st.qtyBold);
        }
    }

    // ------------------------------------------------------------------
    // Cellules
    // ------------------------------------------------------------------

    /** Nom d'onglet Excel : 31 caracteres au plus, sans \ / ? * [ ] : */
    private static String sheetName(String raw) {
        String clean = raw.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        return clean.length() > 31 ? clean.substring(0, 31).trim() : clean;
    }

    private static void merged(Sheet sheet, int rowIndex, String value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < 3; c++) {
            row.createCell(c).setCellStyle(style);
        }
        row.getCell(0).setCellValue(value);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 2));
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        cell.setCellStyle(style);
    }

    private static void formula(Row row, int col, String formula, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellFormula(formula);
        cell.setCellStyle(style);
    }

    /** Styles partages : police Cambria comme la fiche papier, bordures fines sur le tableau. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle subtitleLeft;
        final CellStyle plain;
        final CellStyle right;
        final CellStyle header;
        final CellStyle headerRight;
        final CellStyle cell;
        final CellStyle cellBold;
        final CellStyle cellBoldCenter;
        final CellStyle qty;
        final CellStyle qtyBold;

        Styles(XSSFWorkbook wb) {
            Font normal = font(wb, 11, false, false);
            Font bold = font(wb, 11, true, false);
            Font titleFont = font(wb, 16, false, true);
            Font subtitleFont = font(wb, 12, true, false);

            title = style(wb, titleFont, HorizontalAlignment.CENTER, false, false);
            subtitle = style(wb, subtitleFont, HorizontalAlignment.CENTER, false, false);
            subtitleLeft = style(wb, subtitleFont, HorizontalAlignment.LEFT, false, false);
            plain = style(wb, normal, HorizontalAlignment.LEFT, false, false);
            right = style(wb, normal, HorizontalAlignment.RIGHT, false, false);
            header = style(wb, bold, HorizontalAlignment.LEFT, true, true);
            headerRight = style(wb, bold, HorizontalAlignment.RIGHT, true, true);
            cell = style(wb, normal, HorizontalAlignment.LEFT, true, false);
            cellBold = style(wb, bold, HorizontalAlignment.LEFT, true, false);
            cellBoldCenter = style(wb, bold, HorizontalAlignment.CENTER, true, false);
            qty = style(wb, normal, HorizontalAlignment.RIGHT, true, false);
            qtyBold = style(wb, bold, HorizontalAlignment.RIGHT, true, false);
            qty.setDataFormat(wb.createDataFormat().getFormat("0.##"));
            qtyBold.setDataFormat(wb.createDataFormat().getFormat("0.##"));
        }

        private static Font font(XSSFWorkbook wb, int size, boolean bold, boolean underline) {
            Font f = wb.createFont();
            f.setFontName(FONT);
            f.setFontHeightInPoints((short) size);
            f.setBold(bold);
            if (underline) {
                f.setUnderline(Font.U_SINGLE);
            }
            return f;
        }

        private static CellStyle style(XSSFWorkbook wb, Font font, HorizontalAlignment align,
                                       boolean bordered, boolean shaded) {
            CellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setAlignment(align);
            if (bordered) {
                s.setBorderTop(BorderStyle.THIN);
                s.setBorderBottom(BorderStyle.THIN);
                s.setBorderLeft(BorderStyle.THIN);
                s.setBorderRight(BorderStyle.THIN);
            }
            if (shaded) {
                s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return s;
        }
    }
}
