package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.modules.reporting.dto.MissionReport;
import com.sogeco.fleet.modules.reporting.dto.MissionReport.MissionLine;
import com.sogeco.fleet.modules.reporting.dto.MissionReport.VehicleMissions;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.sogeco.fleet.modules.reporting.VehicleExpensePdfWriter.number;

/**
 * Rapport des missions effectuees, A4 paysage : synthese de la periode puis, pour chaque
 * camion, le detail de ses missions (date, itineraire, type, chauffeur, distance, recette,
 * cout, marge). Meme charte que le rapport des depenses.
 */
@Component
public class MissionReportPdfWriter {

    private final PdfBranding branding;

    public MissionReportPdfWriter(PdfBranding branding) {
        this.branding = branding;
    }

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    private static final Color PRIMARY = new Color(0x1E, 0x3A, 0x5F);
    private static final Color HEADER_BG = new Color(0xE8, 0xEE, 0xF5);
    private static final Color ZEBRA = new Color(0xF7, 0xF9, 0xFB);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color BORDER = new Color(0xCB, 0xD5, 0xE1);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.NORMAL, PRIMARY);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.NORMAL, PRIMARY);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, Color.BLACK);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Font.NORMAL, Color.BLACK);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, MUTED);
    private static final Font TH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Font.NORMAL, PRIMARY);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] write(MissionReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 25 + PdfBranding.HEADER_HEIGHT, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer(report, branding));
            document.addTitle("Rapport des missions");
            document.addAuthor("SOGECO Fleet Manager");
            document.open();

            document.add(new Paragraph("Rapport des missions effectuées", TITLE));
            Paragraph period = new Paragraph("Période du %s au %s  ·  %d véhicule(s)"
                    .formatted(DATE.format(report.from()), DATE.format(report.to()), report.vehicles().size()), SMALL);
            period.setSpacingAfter(12);
            document.add(period);

            PdfPTable kpis = new PdfPTable(2);
            kpis.setWidthPercentage(100);
            kpis.addCell(kpi("Missions terminées", String.valueOf(report.missionCount())));
            kpis.addCell(kpi("Distance", number(report.totalKm()) + " km"));
            document.add(kpis);

            if (report.vehicles().isEmpty()) {
                Paragraph none = new Paragraph("Aucune mission terminée sur la période.", BODY);
                none.setSpacingBefore(16);
                document.add(none);
            }

            for (VehicleMissions v : report.vehicles()) {
                Paragraph h = new Paragraph(v.registrationNumber()
                        + (v.description().isEmpty() ? "" : "  ·  " + v.description())
                        + "  ·  " + v.missionCount() + " mission(s)", H2);
                h.setSpacingBefore(16);
                h.setSpacingAfter(0);
                document.add(h);
                document.add(missionTable(v));
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Echec de generation du PDF", e);
        }
        return out.toByteArray();
    }

    private PdfPTable missionTable(VehicleMissions v) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{1.8f, 1.7f, 2.4f, 4.4f, 2.4f, 1f});
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        header(t, "Mission", "Clôture", "Type", "Itinéraire", "Chauffeur", "Km");
        int row = 0;
        for (MissionLine m : v.missions()) {
            String date = m.end() == null ? "—" : DATETIME.format(m.end().atZone(ZONE));
            line(t, row++, false, m.missionNumber(), date, m.typeLabel(), m.origin() + " > " + m.destination(),
                    m.driverName(), number(m.distanceKm()));
        }
        line(t, 0, true, "Total", "", "", "", "", number(v.totalKm()));
        return t;
    }

    private PdfPCell kpi(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER);
        cell.setPadding(8);
        cell.addElement(new Paragraph(label, SMALL));
        cell.addElement(new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.NORMAL, PRIMARY)));
        return cell;
    }

    /** Colonnes de texte (les 5 premieres) alignees a gauche, les montants a droite. */
    private void header(PdfPTable t, String... labels) {
        for (int i = 0; i < labels.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(labels[i], TH));
            c.setBackgroundColor(HEADER_BG);
            c.setBorderColor(BORDER);
            c.setPadding(4);
            c.setHorizontalAlignment(i < 5 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(c);
        }
        t.setHeaderRows(1);
    }

    private void line(PdfPTable t, int rowIndex, boolean total, String... values) {
        for (int i = 0; i < values.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(values[i] == null ? "" : values[i], total ? BOLD : BODY));
            c.setBorderColor(BORDER);
            c.setPadding(4);
            c.setHorizontalAlignment(i < 5 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            if (total) {
                c.setBackgroundColor(HEADER_BG);
            } else if (rowIndex % 2 == 1) {
                c.setBackgroundColor(ZEBRA);
            }
            t.addCell(c);
        }
    }

    private static class Footer extends PdfPageEventHelper {
        private final MissionReport report;
        private final PdfBranding branding;

        Footer(MissionReport report, PdfBranding branding) {
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
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(left, SMALL), document.leftMargin(), 28, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Page " + writer.getPageNumber(), SMALL),
                    page.getWidth() - document.rightMargin(), 28, 0);
        }
    }
}
