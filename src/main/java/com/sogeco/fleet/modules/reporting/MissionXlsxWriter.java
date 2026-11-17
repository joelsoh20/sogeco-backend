package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.modules.reporting.dto.MissionReport;
import com.sogeco.fleet.modules.reporting.dto.MissionReport.MissionLine;
import com.sogeco.fleet.modules.reporting.dto.MissionReport.VehicleMissions;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Classeur « Suivi quotidien de la productivite des camions », une feuille par camion, mis en
 * page comme le tableau de la logistique : trajets (en ville, SOGECO YDE, BAF, voyage pour DLA),
 * itineraire, tonnage transporte, cout du carburant et cout des reparations, puis la ligne TOTAL.
 *
 * Un trajet « hors ville » est une mission de type VOYAGE_HORS_VILLE ; il est compte dans la
 * colonne du site de destination (SOGECO YDE ou BAF) quand la destination est ce site, sinon
 * dans « VOYAGE POUR DLA ». Toute autre mission est un trajet « en ville ».
 */
@Component
public class MissionXlsxWriter {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String VOYAGE = "VOYAGE_HORS_VILLE";
    private static final int COLS = 11;

    public byte[] write(MissionReport report) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            if (report.vehicles().isEmpty()) {
                Sheet sheet = wb.createSheet("Productivité");
                titleRows(sheet, st, report, "");
                text(sheet.createRow(4), 0, "Aucune mission terminée sur la période.", st.plain);
            }
            int index = 1;
            for (VehicleMissions v : report.vehicles()) {
                writeVehicle(wb, st, report, v, index++);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Echec de generation du fichier Excel", e);
        }
    }

    private void titleRows(Sheet sheet, Styles st, MissionReport r, String subtitle) {
        String month = r.from().getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH).toUpperCase();
        boolean oneMonth = r.from().getYear() == r.to().getYear() && r.from().getMonth() == r.to().getMonth();
        String period = oneMonth ? month + " " + r.from().getYear()
                : DATE.format(r.from()) + " AU " + DATE.format(r.to());
        merged(sheet, 0, "Table 1: Suivi Quotidien de la Productivité des Camions " + period, st.title);
        merged(sheet, 1, subtitle, st.subtitle);
    }

    private void writeVehicle(XSSFWorkbook wb, Styles st, MissionReport r, VehicleMissions v, int index) {
        Sheet sheet = wb.createSheet(sheetName(v.registrationNumber(), index));
        int[] widths = {13, 12, 13, 9, 11, 46, 13, 13, 13, 13, 14};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        titleRows(sheet, st, r, (v.registrationNumber() + " " + v.description()).trim().toUpperCase());

        boolean oneMonth = r.from().getYear() == r.to().getYear() && r.from().getMonth() == r.to().getMonth();
        Row group = sheet.createRow(2);
        for (int c = 0; c < COLS; c++) {
            group.createCell(c).setCellStyle(st.groupGrey);
        }
        text(group, 0, oneMonth ? r.from().getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH).toUpperCase() : "PÉRIODE", st.groupMonth);
        fill(group, 1, 4, "TRAJET", st.groupBlue);
        fill(group, 6, 7, "TONNAGE", st.groupOrange);
        fill(group, 8, 9, "COUT DE CARBURANT", st.groupGreen);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 1, 4));
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 6, 7));
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 8, 9));

        Row head = sheet.createRow(3);
        head.setHeightInPoints(48);
        String[] labels = {"Date", "Type de Trajet\n(En Ville)", "SOGECO YDE", "BAF", "VOYAGE POUR DLA",
                "Itinéraire/Description du Trajet", "Tonnage Transporté\n(En Ville)", "Tonnage Transporté\n(Hors Ville)",
                "Coût du Carburant\n(En Ville)", "Coût du Carburant\n(Hors Ville)", "Coût des Réparations"};
        for (int c = 0; c < labels.length; c++) {
            text(head, c, labels[c], c == 5 ? st.headLeft : st.head);
        }

        int row = 4;
        int first = row;
        for (MissionLine m : v.missions()) {
            Row line = sheet.createRow(row++);
            boolean outside = VOYAGE.equals(m.typeCode());
            text(line, 0, m.end() == null ? "" : DATE.format(m.end().atZone(ZONE)), st.cell);
            for (int c = 1; c <= 4; c++) {
                text(line, c, "", st.center);
            }
            if (!outside) {
                number(line, 1, BigDecimal.ONE, st.center);
            } else if (matches(m.destinationAgencyCity(), "yaound")) {
                number(line, 2, BigDecimal.ONE, st.center);
            } else if (matches(m.destinationAgencyCity(), "bafoussam")) {
                number(line, 3, BigDecimal.ONE, st.center);
            } else {
                number(line, 4, BigDecimal.ONE, st.center);
            }
            String via = m.stops().isEmpty() ? "" : String.join(" - ", m.stops()) + " - ";
            text(line, 5, (m.origin() + " - " + via + m.destination()).toUpperCase(), outside ? st.itineraryVoyage : st.cell);
            BigDecimal tonnes = m.cargoKg().divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
            text(line, 6, "", st.center);
            text(line, 7, "", st.center);
            text(line, 8, "", st.money);
            text(line, 9, "", st.money);
            text(line, 10, "", st.money);
            if (tonnes.signum() > 0) {
                number(line, outside ? 7 : 6, tonnes, st.center);
            }
            if (m.fuelCost().signum() > 0) {
                number(line, outside ? 9 : 8, m.fuelCost(), st.money);
            }
        }
        int last = row - 1;

        Row total = sheet.createRow(row);
        text(total, 0, "TOTAL", st.totalLeft);
        for (int c = 1; c <= 4; c++) {
            sumOrZero(total, c, first, last, st.totalCenter, v.missions().isEmpty());
        }
        text(total, 5, "TOTAL DE TONNAGE", st.totalRight);
        for (int c = 6; c <= 9; c++) {
            sumOrZero(total, c, first, last, c <= 7 ? st.totalCenter : st.totalMoney, v.missions().isEmpty());
        }
        number(total, 10, v.repairCost(), st.totalMoney);
        sheet.createFreezePane(0, 4);
    }

    private static boolean matches(String city, String prefix) {
        return city != null && city.toLowerCase(Locale.ROOT).replace("é", "e").startsWith(prefix);
    }

    private static void sumOrZero(Row row, int col, int first, int last, XSSFCellStyle style, boolean empty) {
        Cell cell = row.createCell(col);
        if (empty) {
            cell.setCellValue(0);
        } else {
            String letter = String.valueOf((char) ('A' + col));
            cell.setCellFormula("SUM(%s%d:%s%d)".formatted(letter, first + 1, letter, last + 1));
        }
        cell.setCellStyle(style);
    }

    /** Nom d'onglet : immatriculation sans caracteres interdits, 31 caracteres au plus, unique. */
    private static String sheetName(String registration, int index) {
        String clean = registration.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (clean.isEmpty()) {
            clean = "Camion " + index;
        }
        return clean.length() > 31 ? clean.substring(0, 31).trim() : clean;
    }

    private static void merged(Sheet sheet, int rowIndex, String value, XSSFCellStyle style) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < COLS; c++) {
            row.createCell(c).setCellStyle(style);
        }
        row.getCell(0).setCellValue(value);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, COLS - 1));
    }

    private static void fill(Row row, int from, int to, String value, XSSFCellStyle style) {
        for (int c = from; c <= to; c++) {
            row.getCell(c).setCellStyle(style);
        }
        row.getCell(from).setCellValue(value);
    }

    private static void text(Row row, int col, String value, XSSFCellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int col, BigDecimal value, XSSFCellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        cell.setCellStyle(style);
    }

    /** Styles : Calibri 11, bordures fines, bandeaux de couleur comme le tableau papier. */
    private static final class Styles {
        final XSSFCellStyle title, subtitle, plain, head, headLeft, cell, center, money;
        final XSSFCellStyle groupGrey, groupMonth, groupBlue, groupOrange, groupGreen, itineraryVoyage;
        final XSSFCellStyle totalLeft, totalRight, totalCenter, totalMoney;

        Styles(XSSFWorkbook wb) {
            XSSFFont normal = font(wb, 11, false, null);
            XSSFFont bold = font(wb, 11, true, null);
            title = style(wb, font(wb, 14, false, null), HorizontalAlignment.CENTER, false, null);
            subtitle = style(wb, font(wb, 11, false, null), HorizontalAlignment.CENTER, false, null);
            plain = style(wb, normal, HorizontalAlignment.LEFT, false, null);
            head = style(wb, bold, HorizontalAlignment.CENTER, true, null);
            headLeft = style(wb, bold, HorizontalAlignment.LEFT, true, null);
            cell = style(wb, normal, HorizontalAlignment.LEFT, true, null);
            center = style(wb, normal, HorizontalAlignment.CENTER, true, null);
            money = style(wb, normal, HorizontalAlignment.CENTER, true, null);
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            itineraryVoyage = style(wb, normal, HorizontalAlignment.LEFT, true, new byte[]{(byte) 0xFF, (byte) 0xD9, 0x66});
            groupGrey = style(wb, bold, HorizontalAlignment.CENTER, true, new byte[]{(byte) 0xD9, (byte) 0xD9, (byte) 0xD9});
            groupMonth = style(wb, font(wb, 11, true, new byte[]{(byte) 0xC0, 0x00, 0x00}),
                    HorizontalAlignment.LEFT, true, new byte[]{(byte) 0xFF, (byte) 0xF2, (byte) 0xCC});
            groupBlue = style(wb, bold, HorizontalAlignment.CENTER, true, new byte[]{(byte) 0xBD, (byte) 0xD7, (byte) 0xEE});
            groupOrange = style(wb, bold, HorizontalAlignment.CENTER, true, new byte[]{(byte) 0xF8, (byte) 0xCB, (byte) 0xAD});
            groupGreen = style(wb, bold, HorizontalAlignment.CENTER, true, new byte[]{(byte) 0xC6, (byte) 0xE0, (byte) 0xB4});
            totalLeft = style(wb, bold, HorizontalAlignment.LEFT, true, null);
            totalRight = style(wb, bold, HorizontalAlignment.RIGHT, true, null);
            totalCenter = style(wb, bold, HorizontalAlignment.CENTER, true, null);
            totalCenter.setDataFormat(wb.createDataFormat().getFormat("0.###"));
            totalMoney = style(wb, bold, HorizontalAlignment.CENTER, true, null);
            totalMoney.setDataFormat(wb.createDataFormat().getFormat("#,##0;-#,##0;\"-\""));
            head.setWrapText(true);
            head.setVerticalAlignment(VerticalAlignment.CENTER);
            headLeft.setWrapText(true);
            headLeft.setVerticalAlignment(VerticalAlignment.CENTER);
        }

        private static XSSFFont font(XSSFWorkbook wb, int size, boolean bold, byte[] rgb) {
            XSSFFont f = wb.createFont();
            f.setFontName("Calibri");
            f.setFontHeightInPoints((short) size);
            f.setBold(bold);
            if (rgb != null) {
                f.setColor(new XSSFColor(rgb, null));
            }
            return f;
        }

        private static XSSFCellStyle style(XSSFWorkbook wb, XSSFFont font, HorizontalAlignment align,
                                           boolean bordered, byte[] fill) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setAlignment(align);
            if (bordered) {
                s.setBorderTop(BorderStyle.THIN);
                s.setBorderBottom(BorderStyle.THIN);
                s.setBorderLeft(BorderStyle.THIN);
                s.setBorderRight(BorderStyle.THIN);
            }
            if (fill != null) {
                s.setFillForegroundColor(new XSSFColor(fill, null));
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return s;
        }
    }
}
