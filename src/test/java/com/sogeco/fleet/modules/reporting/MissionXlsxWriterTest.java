package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.modules.reporting.dto.MissionReport;
import com.sogeco.fleet.modules.reporting.dto.MissionReport.MissionLine;
import com.sogeco.fleet.modules.reporting.dto.MissionReport.VehicleMissions;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionXlsxWriterTest {

    private static MissionLine line(String type, String kg, String fuel, String destAgencyCity, String day) {
        return new MissionLine("M1", null, Instant.parse(day), "x", "CADYST BONABERI", "NDOGSIMBI", "Paul",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                type, new BigDecimal(kg), new BigDecimal(fuel), null, destAgencyCity, List.of("MARCHE MBOPPI"));
    }

    @Test
    void productivitySheetTotals() throws Exception {
        List<MissionLine> lines = List.of(
                line("LIVRAISON_CLIENT", "18000", "35500", null, "2026-03-02T10:00:00Z"),
                line("LIVRAISON_CLIENT", "15000", "0", null, "2026-03-13T10:00:00Z"),
                line("VOYAGE_HORS_VILLE", "16000", "0", null, "2026-03-09T10:00:00Z"),
                line("VOYAGE_HORS_VILLE", "16000", "0", "Yaoundé", "2026-03-17T10:00:00Z"));
        var vehicle = new VehicleMissions(1L, "LT 945 HK", "", 4, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, lines, BigDecimal.ZERO);
        var report = new MissionReport(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), Instant.now(), "t",
                4, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(vehicle));

        byte[] file = new MissionXlsxWriter().write(report);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            Sheet s = wb.getSheet("LT 945 HK");
            var total = s.getRow(8);
            assertEquals("TOTAL", total.getCell(0).getStringCellValue());
            assertEquals(2, total.getCell(1).getNumericCellValue());   // en ville
            assertEquals(1, total.getCell(2).getNumericCellValue());   // SOGECO YDE
            assertEquals(1, total.getCell(4).getNumericCellValue());   // voyage pour DLA
            assertEquals(33, total.getCell(6).getNumericCellValue());  // tonnage en ville
            assertEquals(32, total.getCell(7).getNumericCellValue());  // tonnage hors ville
            assertEquals(35500, total.getCell(8).getNumericCellValue());
        }
    }
}
