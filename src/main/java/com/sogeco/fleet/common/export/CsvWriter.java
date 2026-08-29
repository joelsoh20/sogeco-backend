package com.sogeco.fleet.common.export;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Ecriture CSV minimale, sans dependance.
 *
 * Point d'attention pour Excel : le BOM UTF-8 en tete de fichier
 * evite qu'Excel n'interprete les accents comme du Windows-1252 a
 * l'ouverture — un piege classique avec les donnees francophones.
 */
public final class CsvWriter {

    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    private CsvWriter() {
    }

    public static byte[] write(List<String> headers, List<List<String>> rows) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            buffer.write(UTF8_BOM);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        try (PrintWriter writer = new PrintWriter(buffer, true, StandardCharsets.UTF_8)) {
            writer.println(String.join(";", headers.stream().map(CsvWriter::escape).toList()));
            for (List<String> row : rows) {
                writer.println(String.join(";", row.stream().map(CsvWriter::escape).toList()));
            }
        }

        return buffer.toByteArray();
    }

    /** Point-virgule comme separateur : convention francophone, Excel FR l'attend par defaut. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(";") || value.contains("\"") || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }
}
