package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.modules.setting.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Entete commune des PDF : logo et raison sociale (parametre company.name) en haut de chaque
 * page, suivis d'un filet. Les marges haute des documents doivent laisser {@link #HEADER_HEIGHT}.
 *
 * Le logo (resources/branding/logo.jpeg) est un carre avec beaucoup de marge blanche : il est
 * recadre autour du dessin au chargement. Logo introuvable ou illisible : entete sans logo.
 */
@Slf4j
@Component
public class PdfBranding {

    /** Hauteur reservee en haut de page, en points. */
    public static final float HEADER_HEIGHT = 62;

    private static final Color PRIMARY = new Color(0x1E, 0x3A, 0x5F);
    private static final Color BORDER = new Color(0xCB, 0xD5, 0xE1);
    private static final Font NAME = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.NORMAL, PRIMARY);

    private final SettingService settingService;
    private final byte[] logo;

    public PdfBranding(SettingService settingService) {
        this.settingService = settingService;
        this.logo = loadLogo();
    }

    /** A appeler depuis onEndPage de chaque document. */
    public void drawHeader(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();
        Rectangle page = document.getPageSize();
        float left = document.leftMargin();
        float right = page.getWidth() - document.rightMargin();
        float top = page.getHeight() - 20;
        float x = left;

        if (logo != null) {
            try {
                Image image = Image.getInstance(logo);
                image.scaleToFit(70, 34);
                image.setAbsolutePosition(x, top - image.getScaledHeight());
                cb.addImage(image);
                x += image.getScaledWidth() + 10;
            } catch (Exception e) {
                log.warn("Logo non ajoute a l'entete du PDF : {}", e.getMessage());
            }
        }

        String name = settingService.getString("company.name", "SOGECO");
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(name, NAME), x, top - 22, 0);

        cb.setColorStroke(BORDER);
        cb.setLineWidth(0.8f);
        cb.moveTo(left, top - 40);
        cb.lineTo(right, top - 40);
        cb.stroke();
    }

    private static byte[] loadLogo() {
        try (InputStream in = PdfBranding.class.getResourceAsStream("/branding/logo.jpeg")) {
            if (in == null) {
                return null;
            }
            BufferedImage source = ImageIO.read(in);
            // Zone du dessin dans l'image carree d'origine (proportions conservees quelle que soit la taille).
            int w = source.getWidth();
            int h = source.getHeight();
            BufferedImage cropped = source.getSubimage(Math.round(w * 0.17f), Math.round(h * 0.26f),
                    Math.round(w * 0.66f), Math.round(h * 0.44f));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(cropped, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
