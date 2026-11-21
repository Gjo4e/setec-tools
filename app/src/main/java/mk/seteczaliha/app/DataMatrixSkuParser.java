package mk.seteczaliha.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative SKU suggestion: only labelled fields, never arbitrary Data Matrix digits. */
public final class DataMatrixSkuParser {
    private static final Pattern LABELLED=Pattern.compile(
        "(?i)(?:sku|setec[ _-]?sku|шифра|sifra|product[ _-]?code|item[ _-]?code|code|код)\\s*[:=#-]\\s*([0-9]{4,8})(?![0-9])");
    private DataMatrixSkuParser(){}
    public static String suggest(String raw){
        if(raw==null)return null;
        String trimmed=raw.trim();
        if(trimmed.matches("[0-9]{4,8}"))return trimmed;
        Matcher m=LABELLED.matcher(trimmed);
        return m.find()?m.group(1):null;
    }
}
