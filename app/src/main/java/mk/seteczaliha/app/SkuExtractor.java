package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract SETEC's retail SKU from the printed 'Шифра:' field, not the
 * 8-digit identifier below it or the larger prices on the right.
 * Does not claim OCR succeeded: caller always shows editable confirmation.
 */
public final class SkuExtractor {
    private SkuExtractor() {}
    private static final Pattern ANCHOR = Pattern.compile(
            "(?iu)(?:шифра|shifra|sifra|шuфра|s[hіi]fra)\\s*[:：.]?\\s*([0-9]{4,6})(?![0-9])");
    private static final Pattern LABEL = Pattern.compile("(?iu)(?:шифра|shifra|sifra|шuфра|s[hіi]fra)");
    private static final Pattern NUMBER = Pattern.compile("(?<![0-9])[0-9]{4,6}(?![0-9])");
    private static final Pattern PRICES = Pattern.compile("(?iu)(?:цена|ден\\.?|мкд|price|\\b12x\\b|\\b24x\\b)");

    public static String extract(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String text = raw.replace('\u00a0', ' ');
        Matcher inline = ANCHOR.matcher(text);
        if (inline.find()) return inline.group(1);

        String[] lines = text.split("[\\r\\n]+");
        for (int i = 0; i < lines.length; i++) {
            if (LABEL.matcher(lines[i]).find()) {
                // A photographed price label often prints the number on the next line.
                for (int j = i + 1; j <= i + 2 && j < lines.length; j++) {
                    Matcher m = NUMBER.matcher(lines[j]);
                    if (m.find() && !PRICES.matcher(lines[j]).find()) return m.group();
                }
            }
        }

        // Do not guess from random five-digit prices when the field label is absent.
        return null;
    }
}
