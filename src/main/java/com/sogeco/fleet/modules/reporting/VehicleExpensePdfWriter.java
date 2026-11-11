package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.modules.reporting.dto.VehicleExpenseReport;
import com.sogeco.fleet.modules.reporting.dto.VehicleExpenseReport.Domain;
import com.sogeco.fleet.modules.reporting.dto.VehicleExpenseReport.MissionTypeLine;
import com.sogeco.fleet.modules.reporting.dto.VehicleExpenseReport.VehicleSection;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mise en page du rapport des depenses par camion (A4 portrait).
 *
 * Structure : page de synthese de la flotte (depenses par domaine, bilan des
 * missions par type, classement des camions), puis une page par camion.
 * Police Helvetica standard (accents francais couverts) : aucun fichier de
 * police a embarquer.
 */
@Component
public class VehicleExpensePdfWriter {

    private final PdfBranding branding;

    public VehicleExpensePdfWriter(PdfBranding branding) {
        this.branding = branding;
    }

    private static final Color PRIMARY = new Color(0x1E, 0x3A, 0x5F);
    private static final Color HEADER_BG = new Color(0xE8, 0xEE, 0xF5);
    private static final Color ZEBRA = new Color(0xF7, 0xF9, 0xFB);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color BORDER = new Color(0xCB, 0xD5, 0xE1);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.NORMAL, PRIMARY);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.NORMAL, PRIMARY);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Font.NORMAL, Color.BLACK);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Font.NORMAL, Color.BLACK);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, MUTED);
    private static final Font TH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, PRIMARY);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    public byte[] write(VehicleExpenseReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 30 + PdfBranding.HEADER_HEIGHT, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer(report, branding));
            document.addTitle("Rapport des depenses par vehicule");
            document.addAuthor("SOGECO Fleet Manager");
            document.open();

            writeSummary(document, report);
            for (VehicleSection section : report.vehicles()) {
                document.newPage();
                writeVehicle(document, report, section);
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Echec de generation du PDF", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // Synthese de la flotte
    // ------------------------------------------------------------------

    private void writeSummary(Document doc, VehicleExpenseReport r) throws DocumentException {
        doc.add(new Paragraph("Rapport des dépenses par véhicule", TITLE));
        Paragraph period = new Paragraph("Période du %s au %s  ·  %d véhicule(s)"
                .formatted(DATE.format(r.from()), DATE.format(r.to()), r.vehicles().size()), BODY);
        period.setSpacingAfter(14);
        doc.add(period);

        // Chiffres cles
        PdfPTable kpis = new PdfPTable(3);
        kpis.setWidthPercentage(100);
        kpis.addCell(kpi("Dépenses totales", fcfa(r.fleetTotalExpenses())));
        kpis.addCell(kpi("Missions terminées", String.valueOf(r.fleetMissionCount())));
        kpis.addCell(kpi("Recettes des missions", fcfa(r.fleetRevenue())));
        kpis.addCell(kpi("Distance parcourue", number(r.fleetKm()) + " km"));
        kpis.addCell(kpi("Carburant d'essai (garage)", number(r.fleetTestFuelLiters()) + " L"));
        kpis.addCell(kpi("Temps en panne", r.fleetDowntimeDays() + " j"));
        kpis.completeRow();
        kpis.setSpacingAfter(14);
        doc.add(kpis);

        doc.add(heading("Dépenses par domaine (toute la flotte)"));
        PdfPTable domains = table(new float[]{5, 3, 2});
        header(domains, "Domaine", "Montant", "Part");
        int row = 0;
        for (Domain d : Domain.values()) {
            BigDecimal amount = r.fleetExpenses().getOrDefault(d, BigDecimal.ZERO);
            line(domains, row++, d.label(), fcfa(amount), percent(amount, r.fleetTotalExpenses()));
        }
        totalLine(domains, "Total", fcfa(r.fleetTotalExpenses()), r.fleetTotalExpenses().signum() == 0 ? "" : "100 %");
        doc.add(domains);

        doc.add(heading("Missions par type de mission (toute la flotte)"));
        doc.add(missionTypeTable(r.fleetMissionTypes(), r.fleetMissionCount(), r.fleetKm(), r.fleetRevenue()));

        doc.add(heading("Classement des véhicules par dépenses"));
        PdfPTable ranking = table(new float[]{1, 3, 3, 2, 2});
        header(ranking, "#", "Véhicule", "Dépenses", "Part", "Missions");
        int rank = 1;
        for (VehicleSection v : r.vehicles()) {
            line(ranking, rank - 1, String.valueOf(rank++), v.registrationNumber(),
                    fcfa(v.totalExpenses()), percent(v.totalExpenses(), r.fleetTotalExpenses()),
                    String.valueOf(v.missionCount()));
        }
        doc.add(ranking);
    }

    // ------------------------------------------------------------------
    // Page d'un camion
    // ------------------------------------------------------------------

    private void writeVehicle(Document doc, VehicleExpenseReport r, VehicleSection v) throws DocumentException {
        Paragraph title = new Paragraph(v.registrationNumber(), TITLE);
        doc.add(title);
        String meta = v.description().isEmpty() ? "" : v.description();
        if (v.cityName() != null) {
            meta += (meta.isEmpty() ? "" : "  ·  ") + v.cityName();
        }
        Paragraph sub = new Paragraph(meta + (meta.isEmpty() ? "" : "  ·  ")
                + "du %s au %s".formatted(DATE.format(r.from()), DATE.format(r.to())), SMALL);
        sub.setSpacingAfter(12);
        doc.add(sub);

        PdfPTable kpis = new PdfPTable(3);
        kpis.setWidthPercentage(100);
        kpis.addCell(kpi("Dépenses totales", fcfa(v.totalExpenses())));
        kpis.addCell(kpi("Part de la flotte", percent(v.totalExpenses(), r.fleetTotalExpenses())));
        kpis.addCell(kpi("Distance (missions)", number(v.distanceKm()) + " km"));
        kpis.addCell(kpi("Dépense par km", v.expensePerKm() == null ? "—" : fcfa(v.expensePerKm())));
        kpis.addCell(kpi("Carburant d'essai (garage)", number(v.testFuelLiters()) + " L"));
        kpis.addCell(kpi("Temps en panne", v.downtimeDays() + " j"));
        kpis.completeRow();
        kpis.setSpacingAfter(10);
        doc.add(kpis);

        doc.add(heading("Dépenses par domaine"));
        PdfPTable t = table(new float[]{5, 3, 2});
        header(t, "Domaine", "Montant", "Part");
        int row = 0;
        for (Domain d : Domain.values()) {
            BigDecimal amount = v.expenses().getOrDefault(d, BigDecimal.ZERO);
            line(t, row++, d.label(), fcfa(amount), percent(amount, v.totalExpenses()));
        }
        totalLine(t, "Total des dépenses", fcfa(v.totalExpenses()), v.totalExpenses().signum() == 0 ? "" : "100 %");
        doc.add(t);

        doc.add(heading("Missions par type de mission"));
        if (v.missionTypes().isEmpty()) {
            doc.add(new Paragraph("Aucune mission terminée sur la période.", SMALL));
        } else {
            doc.add(missionTypeTable(v.missionTypes(), v.missionCount(), v.distanceKm(), v.revenue()));
        }
    }

    private PdfPTable missionTypeTable(List<MissionTypeLine> lines, long totalCount, BigDecimal totalKm,
                                       BigDecimal totalRevenue) {
        PdfPTable t = table(new float[]{4, 1.5f, 2.2f, 2.8f});
        header(t, "Type de mission", "Missions", "Distance", "Recettes");
        int row = 0;
        for (MissionTypeLine l : lines) {
            line(t, row++, l.typeLabel(), String.valueOf(l.missionCount()),
                    number(l.distanceKm()) + " km", fcfa(l.revenue()));
        }
        totalLine(t, "Total", String.valueOf(totalCount), number(totalKm) + " km", fcfa(totalRevenue));
        return t;
    }

    // ------------------------------------------------------------------
    // Briques de mise en forme
    // ------------------------------------------------------------------

    private Paragraph heading(String text) {
        Paragraph p = new Paragraph(text, H2);
        p.setSpacingBefore(16);
        // L'espacement sous un titre n'est pas respecte avant un PdfPTable (le tableau
        // recouvrait le titre) : il est porte par le tableau, voir table().
        p.setSpacingAfter(0);
        return p;
    }

    private PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        return t;
    }

    private PdfPCell kpi(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER);
        cell.setPadding(8);
        cell.addElement(new Paragraph(label, SMALL));
        Paragraph v = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.NORMAL, PRIMARY));
        cell.addElement(v);
        return cell;
    }

    private void header(PdfPTable t, String... labels) {
        for (int i = 0; i < labels.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(labels[i], TH));
            c.setBackgroundColor(HEADER_BG);
            c.setBorderColor(BORDER);
            c.setPadding(5);
            c.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(c);
        }
        t.setHeaderRows(1);
    }

    private void line(PdfPTable t, int rowIndex, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i], BODY));
            c.setBorderColor(BORDER);
            c.setPadding(5);
            c.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            if (rowIndex % 2 == 1) {
                c.setBackgroundColor(ZEBRA);
            }
            t.addCell(c);
        }
    }

    private void totalLine(PdfPTable t, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i], BOLD));
            c.setBorderColor(BORDER);
            c.setPadding(5);
            c.setBackgroundColor(HEADER_BG);
            c.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(c);
        }
    }

    /** Pied de page : confidentialite, date de generation et numero de page. */
    private static class Footer extends PdfPageEventHelper {
        private final VehicleExpenseReport report;
        private final PdfBranding branding;

        Footer(VehicleExpenseReport report, PdfBranding branding) {
            this.report = report;
            this.branding = branding;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            branding.drawHeader(writer, document);
            PdfContentByte cb = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            String left = "SOGECO Fleet Manager  ·  généré le %s par %s"
                    .formatted(DATETIME.format(report.generatedAt().atZone(ZONE)), report.generatedBy());
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(left, SMALL),
                    document.leftMargin(), 28, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), SMALL),
                    page.getWidth() - document.rightMargin(), 28, 0);
        }
    }

    // ------------------------------------------------------------------
    // Formats (espace simple comme separateur : le fin insecable n'existe pas en WinAnsi)
    // ------------------------------------------------------------------

    static String fcfa(BigDecimal amount) {
        return number(amount) + " FCFA";
    }

    static String number(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        String s = value.setScale(0, RoundingMode.HALF_UP).toPlainString();
        boolean negative = s.startsWith("-");
        if (negative) {
            s = s.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(s.charAt(i));
        }
        return (negative ? "-" : "") + sb;
    }

    static String percent(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0 || part == null) {
            return "—";
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',') + " %";
    }
}
