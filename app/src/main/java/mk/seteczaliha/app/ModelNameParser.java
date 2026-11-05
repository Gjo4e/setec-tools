package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Conservative OCR candidate selection for a manually photographed model label.
 * Ignores prices/installments and joins adjacent OCR lines when the model wraps.
 * No web scraping or network-dependent heuristics; testable without Android.
 */
final class ModelNameParser {
    static final class Candidate {
        final String model;
        final int score;
        Candidate(String model, int score) { this.model = model; this.score = score; }
    }

    private static final Pattern BRAND = Pattern.compile(
        "(?i)\\b(?:APPLE|ASUS|ASROCK|MSI|GIGABYTE|INTEL|AMD|KINGSTON|SAMSUNG|ADATA|CORSAIR|" +
        "CRUCIAL|WESTERN DIGITAL|WD|SEAGATE|NOCTUA|DEEPCOOL|COOLER MASTER|NZXT|SAPPHIRE|" +
        "PALIT|ZOTAC|THERMALTAKE|BE QUIET|TEAMGROUP|G\\.SKILL|LENOVO|DELL|HP|ACER|" +
        "SONY|LG|HUAWEI|XIAOMI|LOGITECH|RAZER|MICROSOFT|FUJITSU|TOSHIBA|MSI)\\b");
    private static final Pattern FAMILY = Pattern.compile(
        "(?i)\\b(?:MACBOOK|IMAC|MAC\\s*MINI|MAC\\s*STUDIO|IPAD|IPHONE|GALAXY|" +
        "THINKPAD|IDEAPAD|LEGION|YOGA|VIVOBOOK|ZENBOOK|EXPERTBOOK|TUF|ROG|PREDATOR|" +
        "ASPIRE|NITRO|INSPIRON|LATITUDE|OPTIPLEX|XPS|PAVILION|ELITEBOOK|PROBOOK|" +
        "OMEN|VICTUS|SURFACE|SWIFT|NVIDIA|GEFORCE|RADEON|RYZEN|CORE\\s*ULTRA|" +
        "PRIME|AORUS|MAG|MEG|VENTUS|FURY|VENGEANCE|TRIDENT|SNV\\w+|KVR\\w+)\\b");
    private static final Pattern CODE = Pattern.compile(
        "(?i)\\b(?=[A-Z0-9._/-]*[A-Z])(?=[A-Z0-9._/-]*\\d)[A-Z0-9][A-Z0-9._/-]{3,}\\b");
    private static final Pattern PRICING = Pattern.compile(
        "(?iu)(?:\\b\\d{1,2}\\s*[XХ]\\s*\\d{2,}\\b|" +
        "\\b(?:RATE|INSTALLMENTS|RATI|РАТИ|РАТА|ПЛАЌАЊЕ|CENA|ЦЕНА|КЛУБ\\s*ЦЕНА|КЛУБ|ДЕН|ДЕН\\.|ДЕНАР|МКД|MKD)\\b|" +
        "\\b\\d+[.,]\\d{3}\\s*(?:ДЕН|MKD|DEN)\\b|" +
        "\\b\\d+\\s*(?:GB|TB)\\s*(?:MEMORIJA|МЕМОРИЈА)\\s+ЦЕНА\\b)");
    private static final Pattern NOISE = Pattern.compile(
        "(?iu)(?:\\b(?:ШИФРА|БАРКОД|BARCODE|SERIAL|IMEI|EAN|ГАРАНЦИЈА|WARRANTY|ДОСТАПНОСТ|ДОСТАВА)\\b|" +
        "https?://|www\\.|\\b\\d{4,}\\s*(?:DEN|ДЕН)\\b)");
    private static final Pattern APPLE_MACBOOK = Pattern.compile(
        "(?i)\\bMACB[O0]{2}K\\s+(NE[O0]|AIR|PRO)(?:\\s+(\\d{1,2}))?");
    private static final Pattern APPLE_COLOR = Pattern.compile(
        "(?i)\\b(INDIGO|SILVER|CITRUS|BLUSH|MIDNIGHT|STARLIGHT|SPACE\\s+GRAY|BLACK|WHITE|GOLD)\\b");

    /** Build a searchable model name, not the whole noisy price label. */
    static String normalizeModel(String label) {
        if(label==null)return "";
        String text=label.replaceAll("[\\r\\n\\t]+"," ").replaceAll("\\s+"," ").trim();
        Matcher mac=APPLE_MACBOOK.matcher(text);
        if(mac.find()){
            String family=mac.group(1).replace('0','O').toUpperCase(Locale.ROOT);
            String size=mac.group(2)==null?"":" "+mac.group(2);
            Matcher color=APPLE_COLOR.matcher(text.substring(mac.end()));
            String variant=color.find()?" "+color.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+"," "):"";
            return "Apple MacBook "+family+size+variant;
        }
        text=text.replaceAll("[“”″‘’()\\[\\]]"," ").replaceAll("\\s+"," ").trim();
        text=text.replaceFirst("(?iu)^(?:MODEL(?:\\s+NO\\.?|\\s+NUMBER)?|МОДЕЛ)\\s*[:#-]?\\s*","");
        text=GENERIC_START.matcher(text).replaceFirst("").trim();
        return text.replaceAll("^[^\\p{L}\\d]+|[^\\p{L}\\d]+$","").trim();
    }

    private static final Pattern GENERIC_START = Pattern.compile(
        "(?iu)^(?:ЛАПТОП|НОУТБУК|LAPTOP|NOTEBOOK|КОМПЈУТЕР|МАТИЧНА\\s+ПЛОЧА|" +
        "MOTHERBOARD|ПРОЦЕСОР|ГРАФИЧКА\\s+КАРТИЧКА|SSD\\s+ДИСК|RAM\\s+МЕМОРИЈА)\\s+");

    private ModelNameParser() {}

    static List<Candidate> rank(List<String> lines) {
        Map<String, Candidate> found = new LinkedHashMap<>();
        if (lines == null) return new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            StringBuilder combined = new StringBuilder();
            // Labels often put the brand, model, and color on separate lines.
            for (int n = 0; n < 4 && i + n < lines.size(); n++) {
                String raw = lines.get(i+n);
                if (raw == null || raw.trim().isEmpty()) break;
                if (isNoise(raw)) break; // Never join a model to payment/price text.
                if (combined.length() > 0) combined.append(' ');
                combined.append(raw);
                offer(found, combined.toString(), n + 1);
            }
        }
        List<Candidate> results = new ArrayList<>(found.values());
        Collections.sort(results, (a,b) -> {
            int compare = Integer.compare(b.score, a.score);
            return compare != 0 ? compare : Integer.compare(b.model.length(), a.model.length());
        });
        if (results.size() > 6) return new ArrayList<>(results.subList(0, 6));
        return results;
    }

    static boolean likelyCorrect(Candidate c) {
        return c != null && c.score >= 22;
    }

    private static boolean isNoise(String raw) {
        String s = raw.toUpperCase(Locale.ROOT);
        if (PRICING.matcher(s).find() || NOISE.matcher(s).find()) return true;
        // OCR often confuses "2 paM 12x 5590 AeH" with a model name.
        if (s.matches(".*\\b\\d{1,2}\\s*[XХ]\\s*\\d{2,}.*")) return true;
        if (s.matches(".*\\b\\d\\s*(?:P|Р)(?:A|А).*\\b\\d{2,}.*")) return true;
        return false;
    }

    private static void offer(Map<String,Candidate> found, String raw, int parts) {
        if (isNoise(raw)) return;
        String clean = normalizeModel(raw);
        if (clean.length() < 4 || clean.length() > 90) return;
        String upper = clean.toUpperCase(Locale.ROOT);
        boolean brand = BRAND.matcher(upper).find();
        boolean family = FAMILY.matcher(upper).find();
        boolean code = CODE.matcher(upper).find();
        boolean digit = upper.matches(".*\\d.*");
        // No brand, family or alphanumeric model identifier means uncertain.
        if (!brand && !family && !code) return;
        if (brand && !family && !code && !digit) return; // e.g. "Apple" alone.
        int score = 0;
        if (brand) score += 9;
        if (family) score += 12;
        if (code) score += 7;
        if (digit) score += 3;
        if (clean.contains("/") || clean.contains("-")) score += 2;
        if (clean.split("\\s+").length >= 2) score += 2;
        if (clean.length() >= 9 && clean.length() <= 65) score += 2;
        score += Math.min(3, parts-1);
        if (upper.matches(".*\\b(?:INDIGO|SILVER|MIDNIGHT|BLACK|WHITE|GRAY|GREY|BLUE|PINK|GOLD|SPACE\\s+GRAY)\\b.*")) score += 1;
        if (score < 11) return;
        String key = upper.replaceAll("\\s+", " ");
        Candidate previous = found.get(key);
        if (previous == null || score > previous.score)
            found.put(key, new Candidate(clean, score));
    }
}
