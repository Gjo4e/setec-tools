package mk.seteczaliha.app;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Setec product pages put sales/spec text after the model's real name.
 * Extract only the product identity before researching it on independent sites.
 * The SKU itself must be verified independently on the Setec product page.
 */
final class SetecProductTitle {
    private static final Pattern PREFIX = Pattern.compile(
        "(?iu)^(?:(?:Лаптоп|Ноутбук|Компјутер|Таблет|Телевизор|ТВ|Монитор|" +
        "Машина\\s+за\\s+перење(?:\\s+алишта)?|Машина\\s+за\\s+садови|" +
        "Фрижидер|Замрзнувач|Клима|Производ|Laptop|Notebook|Tablet|" +
        "Washing\\s+machine)\\s+)+");
    private static final Pattern SPECS = Pattern.compile(
        "(?iu)\\s+[-–—|:]\\s*(?=(?:Процесор|CPU|Processor|RAM|Меморија|Memory|" +
        "SSD|HDD|Графичка|Графика|Graphics|GPU|Екран|Screen|" +
        "Дисплеј|Display|Батерија|Battery|Капацитет|Capacity|" +
        "Гаранција|Warranty|Шифра|SKU|Цена|Price|Клуб\\s+цена)\\b)");
    private static final Pattern INLINE_SPECS = Pattern.compile(
        "(?iu)\\s+(?=(?:Процесор|CPU|Processor|RAM|SSD|HDD|Графичка|" +
        "Гаранција|Шифра|Клуб\\s+цена)\\s*:)");
    // Parenthetical manufacturer model identifier: (AG15-42P-R844).
    private static final Pattern PAREN_CODE = Pattern.compile(
        "(?i)\\(([A-Z0-9][A-Z0-9/_-]{4,})\\)");
    private static final Pattern DUPLICATE_START = Pattern.compile(
        "(?iu)^(\\p{L}{2,})(?:\\s+\\1)+\\b");

    private SetecProductTitle() {}

    static String modelFromProductName(String raw){
        if(raw==null)return "";
        String title=raw.replace('\u00a0',' ').replaceAll("[\\r\\n\\t]+"," ")
            .replaceAll("\\s+"," ").trim();
        title=title.replaceFirst("(?iu)^(?:Краток\\s+опис|Назив|Име\\s+на\\s+производ)\\s*:\\s*","");
        title=PREFIX.matcher(title).replaceFirst("").trim();
        Matcher spec=SPECS.matcher(title);
        if(spec.find())title=title.substring(0,spec.start()).trim();
        Matcher inline=INLINE_SPECS.matcher(title);
        if(inline.find())title=title.substring(0,inline.start()).trim();
        // Once a proper manufacturer code in parentheses is reached, discard
        // any product-page marketing specifications accidentally glued to it.
        Matcher parenthesized=PAREN_CODE.matcher(title);
        if(parenthesized.find()){
            String token=parenthesized.group(1);
            if(token.matches(".*\\d.*")&&token.matches(".*[A-Za-z].*")){
                String suffix=title.substring(parenthesized.end()).trim();
                if(suffix.matches("(?iu)^(?:[-–—|,:.]\\s*)?(?:процесор|cpu|processor|ram|ssd|hdd|memory|меморија|графичка|гаранција|цена|клуб).*"))
                    title=title.substring(0,parenthesized.end());
            }
        }
        title=DUPLICATE_START.matcher(title).replaceFirst("$1");
        title=title.replaceAll("^[\\s\\-–—|,:;]+|[\\s\\-–—|,:;]+$","").trim();
        if(title.length()>110) {
            // Some air conditioners and large appliances have giant marketing
            // titles, yet begin with a precise manufacturer model code.
            // Preserve ONLY the leading brand+code when that identity is clear.
            String[] tokens=title.split("\\s+");
            int lastCode=-1;
            for(int i=0;i<Math.min(tokens.length,10);i++){
                String token=tokens[i].replaceAll("^[()]+|[(),;:.]+$","");
                String upper=token.toUpperCase(Locale.ROOT);
                if(upper.matches(".*(?:BTU|RPM|MAH|MHZ|GHZ|KW|GB|TB|W|КГ|ЛИТРИ)$")||
                   upper.matches(".*(?:КОМПРЕСОР|КЛИМА|ЛАДЕЊЕ|ГРЕЕЊЕ|ИНВЕРТЕР).*"))break;
                if(token.length()>=5&&token.matches("(?iu).*[A-Z].*")&&
                    token.matches(".*\\d.*")&&
                    token.matches("(?iu)[A-Z0-9/()._-]+")){
                    lastCode=i;
                    break;
                }
            }
            if(lastCode>=1){
                StringBuilder exact=new StringBuilder();
                for(int i=0;i<=lastCode;i++){
                    if(i>0)exact.append(' ');
                    exact.append(tokens[i]);
                }
                if(exact.length()<=110)return exact.toString();
            }
            // Unknown oversized product title: reject rather than guess model.
            return "";
        }
        return title;
    }
}
