package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Match the exact scanned product/model even when Setec formats part numbers differently. */
final class SetecModelMatcher {
    private static final Set<String> COLORS = new LinkedHashSet<>(Arrays.asList(
        "INDIGO","SILVER","CITRUS","BLUSH","BLACK","WHITE","BLUE","GREEN",
        "GRAY","GREY","GOLD","RED","PINK","MIDNIGHT","STARLIGHT"));
    private static final Set<String> GENERIC = new LinkedHashSet<>(Arrays.asList(
        "LAPTOP","NOTEBOOK","PRODUCT","MODEL","PC","DESKTOP","TABLET",
        "ЛАПТОП","НОУТБУК","ТАБЛЕТ","КОМПЈУТЕР","ПРОИЗВОД","МОДЕЛ",
        "МАШИНА","ПЕРЕЊЕ","АЛИШТА","ФРИЖИДЕР","ЗАМРЗНУВАЧ","ШПОРЕТ"));
    private static final Set<String> BRANDS = new LinkedHashSet<>(Arrays.asList(
        "MIDEA","GORENJE","BEKO","BOSCH","WHIRLPOOL","CANDY","HISENSE","AEG",
        "ELECTROLUX","SAMSUNG","LG","TESLA","INDESIT","HAIER","VOX","PHILIPS",
        "APPLE","ASUS","LENOVO","DELL","HP","ACER","MSI","KINGSTON","AMD","INTEL"));
    private static final Pattern COMPLEX_CODE = Pattern.compile(
        "(?i)\\b[A-Z]{1,6}\\d[A-Z\\d]{3,}(?:[/-][A-Z\\d]+)*\\b|" +
        "\\b[A-Z\\d]{2,}(?:[/-][A-Z\\d]{1,})+\\b");
    private SetecModelMatcher() {}

    static String clean(String value) {
        String text = ModelNameParser.normalizeModel(value==null?"":value);
        return text.toUpperCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\d]+"," ").trim().replaceAll("\\s+"," ");
    }

    /** For electronics and appliances, preserve letters AND digits of a part number. */
    static String modelCode(String value) {
        String source=ModelNameParser.normalizeModel(value);
        Matcher m=COMPLEX_CODE.matcher(source.toUpperCase(Locale.ROOT));
        String selected="";
        int end=-1;
        while(m.find()){
            String candidate=m.group();
            if(!candidate.matches(".*\\d.*"))continue;
            // Capacities like 8GB/128GB are not the laptop model number.
            if(candidate.matches("(?i)\\d+(?:GB|TB|MB)(?:[/\\-]\\d+(?:GB|TB|MB))*"))continue;
            if(candidate.length()>selected.length()){selected=candidate;end=m.end();}
        }
        if(selected.isEmpty())return "";
        // OCR sometimes produces MF110W70/W HR; on the website it is MF110W70/W-HR.
        String trailing=end<0?"":source.substring(end).trim().toUpperCase(Locale.ROOT);
        if(selected.contains("/")&&trailing.matches("^(?:HR|EU|MK|UK|US|BK|WH|W|S|B)(?:\\b.*)?$")) {
            Matcher suffix=Pattern.compile("^(HR|EU|MK|UK|US|BK|WH|W|S|B)\\b").matcher(trailing);
            if(suffix.find())selected+=suffix.group(1);
        }
        return selected.replaceAll("[^A-Z\\d]","");
    }

    static String searchTerm(String query) {
        String value=ModelNameParser.normalizeModel(query);
        String upper=value.toUpperCase(Locale.ROOT);
        if(upper.contains("MACBOOK")){
            String[] tokens=upper.split("\\s+");
            int idx=Arrays.asList(tokens).indexOf("MACBOOK");
            if(idx>=0&&idx+1<tokens.length)return "MacBook "+tokens[idx+1];
            return "MacBook";
        }
        String model=modelCode(value);
        if(model.length()>=6){
            // Numeric product identity is much more dependable than a full marketing name.
            Matcher m=COMPLEX_CODE.matcher(value.toUpperCase(Locale.ROOT));
            if(m.find())return m.group().split("/")[0];
        }
        String reduced=value.replaceAll("(?i)\\b(INDIGO|SILVER|CITRUS|BLUSH|BLACK|WHITE|GRAY|BLUE)\\b","").trim();
        return reduced.substring(0,Math.min(72,reduced.length()));
    }

    static List<String> searchTerms(String query) {
        String text=ModelNameParser.normalizeModel(query);
        LinkedHashSet<String> terms=new LinkedHashSet<>();
        String first=searchTerm(text);
        if(!first.isEmpty())terms.add(first);
        String rawCode="";
        Matcher matcher=COMPLEX_CODE.matcher(text.toUpperCase(Locale.ROOT));
        if(matcher.find())rawCode=matcher.group();
        if(!rawCode.isEmpty()){
            String prefix=rawCode.split("[/-]")[0];
            if(prefix.length()>=5)terms.add(prefix);
            terms.add(rawCode);
        }
        String normalized=clean(text);
        for(String brand:BRANDS)if(Arrays.asList(normalized.split(" ")).contains(brand)&&!rawCode.isEmpty()){
            terms.add(brand+" "+rawCode.split("[/-]")[0]);break;
        }
        String[] words=text.split("\\s+");
        if(words.length>=2)terms.add(words[0]+" "+words[1]);
        if(!text.isEmpty())terms.add(text);
        List<String> result=new ArrayList<>();
        for(String t:terms)if(!t.trim().isEmpty()&&result.size()<6)result.add(t.trim());
        return result;
    }

    static String possibleOfficialSlug(String query) {
        String normalized=ModelNameParser.normalizeModel(query);
        if(modelCode(normalized).length()<6)return "";
        String[] words=normalized.split("\\s+");
        for(int i=0;i<words.length;i++)if(BRANDS.contains(words[i].toUpperCase(Locale.ROOT))){
            normalized=String.join(" ",Arrays.copyOfRange(words,i,words.length));break;
        }
        String slug=normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-")
            .replaceAll("^-+|-+$","");
        return slug.length()>8&&slug.length()<120?slug:"";
    }

    static int score(String query,String productName) {
        String q=clean(query),p=clean(productName);
        if(q.length()<4||p.length()<4)return -1;
        Set<String> qTokens=new LinkedHashSet<>(Arrays.asList(q.split(" ")));
        Set<String> pTokens=new LinkedHashSet<>(Arrays.asList(p.split(" ")));
        qTokens.removeAll(GENERIC);pTokens.removeAll(GENERIC);
        if(qTokens.size()<2)return -1;
        for(String color:COLORS)if(qTokens.contains(color)&&!pTokens.contains(color))return -1;
        for(String brand:BRANDS)if(qTokens.contains(brand)){
            if(!pTokens.contains(brand))return -1;
            break;
        }
        String expectedCode=modelCode(query),actualCode=modelCode(productName);
        if(!expectedCode.isEmpty()){
            if(actualCode.isEmpty()||!actualCode.equals(expectedCode))return -1;
            // The exact model identifier + same brand is safe even when the seller
            // adds "машина за перење" or omits a marketing family name.
            return 130;
        }
        int matched=0,significant=0,numeric=0;
        for(String token:qTokens){
            if(token.length()<2)continue;
            significant++;
            if(pTokens.contains(token)){matched++;if(token.matches(".*\\d.*"))numeric++;}
            else if(token.matches(".*\\d.*"))return -1;
        }
        if(significant==0||matched<2)return -1;
        int value=matched*100/significant;
        if(matched==significant)value+=10;
        if(numeric>0)value+=5;
        return value>=80?value:-1;
    }
}
