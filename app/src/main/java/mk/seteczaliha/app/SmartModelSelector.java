package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract one product model from a WHOLE photographed store price card / page.
 * Prefer an alphanumeric manufacturer's model code on a headline over labels,
 * prices, installment amounts, warranty, stock, category and SKU identifiers.
 * Keep only distinct model identities, not OCR fragments of the same product.
 * No Android dependency: deterministic regression tests cover full price cards.
 */
final class SmartModelSelector {
    static final class Line {
        final String text;
        final int top, height;
        Line(String text, int top, int height) {
            this.text=text==null?"":text.trim();
            this.top=top;this.height=height;
        }
    }
    private static final Pattern BRAND = Pattern.compile(
        "(?iu)\\b(?:MIDEA|GORENJE|BEKO|BOSCH|WHIRLPOOL|CANDY|HISENSE|AEG|" +
        "ELECTROLUX|SAMSUNG|LG|TESLA|HAIER|VOX|TELEFUNKEN|PHILIPS|VIVAX|" +
        "INDESIT|KONCAR|KONČAR|SENCOR|APPLE|ASUS|ASROCK|MSI|GIGABYTE|" +
        "INTEL|AMD|KINGSTON|CORSAIR|CRUCIAL|SAPPHIRE|LENOVO|DELL|HP|ACER|" +
        "LOGITECH|RAZER|PANASONIC|TOSHIBA|NOCTUA|DEEPCOOL|NZXT|NVIDIA|" +
        "SEAGATE|ADATA|XIAOMI|HUAWEI|SONY|WESTERN DIGITAL)\\b");
    private static final Pattern CODE = Pattern.compile(
        "(?i)(?<![A-Z0-9])(?=[A-Z0-9/_-]{5,}[A-Z])(?=[A-Z0-9/_-]{5,}\\d)" +
        "[A-Z][A-Z0-9]{2,}(?:[-/][A-Z0-9]+)*(?![A-Z0-9])");
    private static final Pattern NUMERIC_SUFFIX_CODE = Pattern.compile(
        "(?i)(?<![A-Z0-9])[0-9]{3,5}[A-Z]{1,3}(?![A-Z0-9])");
    private static final Pattern NOISE = Pattern.compile(
        "(?iu)(?:цена|редовна|клуб|ден(?:ари)?\\.?|мкд|mkd|price|discount|" +
        "попуст|cashback|поени|рати|рата|rate|installment|garancija|" +
        "гаранција|месеци|months|шифра|баркод|barcode|serial|s/n|" +
        "достава|достапност|тел(?:ефон)?\\s*[:.]|www\\.|https?://|" +
        "проценти|рејтинг|обработка|комфорт|премиум)");
    private static final Pattern META_TAIL = Pattern.compile(
        "(?iu)\\s+(?:шифра|цена|гарaнција|гаранција|ден(?:ари)?|" +
        "достапност|редовна|клуб\\s*цена|12\\s*рати|24\\s*рати)\\b.*$");
    private static final Pattern ITEM_PREFIX = Pattern.compile(
        "(?iu)^(?:(?:краток\\s*опис|опис|модел|product\\s*name|" +
        "model\\s*name|brand|бренд)\\s*[:\\-]\\s*|" +
        "(?:машина\\s*за\\s*перење|лаптоп|ноутбук|телевизор|фрижидер|" +
        "производ|модел)\\s+)+");
    private static final Pattern ONLY_BRAND = Pattern.compile(
        "(?i)^\\s*(?:MIDEA|GORENJE|BEKO|BOSCH|WHIRLPOOL|CANDY|HISENSE|" +
        "AEG|ELECTROLUX|SAMSUNG|LG|TESLA|HAIER|VOX|TELEFUNKEN|" +
        "PHILIPS|APPLE|ASUS|MSI|LENOVO|DELL|HP|ACER|KINGSTON)\\s*$");

    private SmartModelSelector(){}

    private static boolean rejected(String raw) {
        if(raw==null||raw.isEmpty())return true;
        String t=raw.toUpperCase(Locale.ROOT);
        if(NOISE.matcher(t).find())return true;
        if(t.matches(".*\\b\\d{1,2}\\s*[XХ]\\s*\\d{2,}.*"))return true;
        if(t.matches(".*\\b\\d+[.,]\\d{3}\\b.*")&&!CODE.matcher(t).find())return true;
        return false;
    }

    private static String cleanLine(String raw){
        String value=raw.replaceAll("\\s+"," ").trim();
        value=ITEM_PREFIX.matcher(value).replaceFirst("");
        value=META_TAIL.matcher(value).replaceFirst("");
        return value.replaceAll("^[^\\p{L}\\d]+|[^\\p{L}\\d/-]+$","").trim();
    }

    private static String modelCode(String line){
        Matcher codes=CODE.matcher(line);
        String best="";
        while(codes.find()){
            String candidate=codes.group().toUpperCase(Locale.ROOT);
            if(candidate.matches("\\d+\\s*(?:GB|TB|MHZ|HZ)")||
                candidate.matches("(?i)(?:DDR[345]|[0-9]+GB|[0-9]+MHZ|USB[0-9]+)")||
                candidate.matches("(?i)(?:RTX|GTX|DDR)[0-9]{1,2}"))continue;
            if(candidate.length()>best.length())best=candidate;
        }
        if(best.isEmpty()&&line.toUpperCase(Locale.ROOT).matches(".*\\bTWM\\s+\\d{3,5}[A-Z].*")){
            Matcher numeric=NUMERIC_SUFFIX_CODE.matcher(line);
            if(numeric.find())best=numeric.group().toUpperCase(Locale.ROOT);
        }
        return best;
    }

    static List<ModelNameParser.Candidate> rank(List<Line> observed){
        if(observed==null||observed.isEmpty())return Collections.emptyList();
        List<Line> lines=new ArrayList<>();
        for(Line line:observed)
            if(line!=null&&!line.text.trim().isEmpty())lines.add(line);
        // OCR usually gives reading order, but sort nearby lines by vertical location
        // to join split brand/model headings on the same price card.
        // Avoid hard ordering across multiple columns; those create separate choices.
        Map<String,ModelNameParser.Candidate> matches=new LinkedHashMap<>();
        int maxModelHeight=1;
        for(Line l:lines)if(!rejected(l.text)&&!modelCode(l.text).isEmpty())
            maxModelHeight=Math.max(maxModelHeight,Math.max(1,l.height));

        for(int i=0;i<lines.size();i++){
            Line line=lines.get(i);
            if(rejected(line.text))continue;
            String cleaned=cleanLine(line.text);
            if(cleaned.isEmpty())continue;
            String code=modelCode(cleaned);
            if(code.isEmpty())continue;
            String name=buildName(cleaned,code);
            if(name.isEmpty())continue;
            boolean brand=BRAND.matcher(name).find();
            if(!brand&&i>0&&!rejected(lines.get(i-1).text)){
                String prev=cleanLine(lines.get(i-1).text);
                // The previous line may contain only a brand or a short brand+series
                // e.g. "MIDEA LUNAR" + "MF110W60/W-HR".
                if(BRAND.matcher(prev).find()&&prev.split("\\s+").length<=3){
                    name=buildName(prev+" "+name,code);
                    brand=BRAND.matcher(name).find();
                }
            }
            int score=(brand?58:31)+Math.min(12,code.length())+
                (name.contains("-")||name.contains("/")?5:0);
            if(line.height>0){
                score+=Math.min(12,Math.round(12f*line.height/maxModelHeight));
            }
            if(name.split("\\s+").length>7)score-=18;
            store(matches,new ModelNameParser.Candidate(name,score),code);
        }
        // For a named model family without an alphanumeric part number
        // (e.g. Apple MacBook NEO 13 INDIGO), use the existing MacBook parser.
        if(matches.isEmpty()){
            List<String> text=new ArrayList<>();
            for(Line line:lines)if(!rejected(line.text))text.add(line.text);
            for(ModelNameParser.Candidate c:ModelNameParser.rank(text)){
                if(c.score>=22)store(matches,c,SetecModelMatcher.modelCode(c.model));
            }
        }
        List<ModelNameParser.Candidate> result=new ArrayList<>(matches.values());
        result.sort((a,b)->Integer.compare(b.score,a.score));
        return result.size()>4?new ArrayList<>(result.subList(0,4)):result;
    }

    private static String buildName(String source,String code){
        String text=source.replaceAll("[“”″‘’()]"," ").replaceAll("\\s+"," ").trim();
        text=ITEM_PREFIX.matcher(text).replaceFirst("").trim();
        int codePos=text.toUpperCase(Locale.ROOT).indexOf(code);
        if(codePos<0){
            // The part number may be spaced as "MF110W60/W HR" in OCR.
            String compactCode=code.replaceAll("[^A-Z0-9]","");
            Matcher m=CODE.matcher(text);
            while(m.find()){
                if(m.group().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","").equals(compactCode)){
                    codePos=m.start();break;
                }
            }
        }
        if(codePos<0)return "";
        Matcher b=BRAND.matcher(text.substring(0,codePos));
        int firstBrand=-1;
        while(b.find())firstBrand=b.start();
        String before=firstBrand>=0?text.substring(firstBrand,codePos).trim():"";
        if(!before.isEmpty()){
            // If a small "Gorenje" caption precedes the bold "GORENJE...",
            // retain only the brand closest to the real model code.
            before=before.replaceAll("(?iu)\\b([\\p{L}]+)(?:\\s+\\1)+\\b","$1").trim();
            String[] tokens=before.split("\\s+");
            if(tokens.length>4){
                before=String.join(" ",Arrays.copyOfRange(tokens,tokens.length-4,tokens.length));
            }
        }
        String end=text.substring(codePos);
        Matcher match=CODE.matcher(end);
        if(!match.find())return "";
        String actualCode=match.group();
        // Short suffix split into separate OCR token: "MF110W60/W HR".
        String tail=end.substring(match.end()).trim();
        if(actualCode.contains("/")&&tail.matches("(?i)^(?:HR|EU|MK|UK|BK|US|WH)\\b.*")){
            Matcher suffix=Pattern.compile("(?i)^(HR|EU|MK|UK|BK|US|WH)\\b").matcher(tail);
            if(suffix.find())actualCode+="-"+suffix.group(1);
        }
        return (before.isEmpty()?"":before+" ")+actualCode;
    }

    private static void store(Map<String,ModelNameParser.Candidate> found,
                              ModelNameParser.Candidate candidate,String code){
        if(candidate.model.length()<4)return;
        String key=code==null||code.isEmpty()
            ?candidate.model.toUpperCase(Locale.ROOT)
            :code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
        ModelNameParser.Candidate old=found.get(key);
        if(old==null||candidate.score>old.score||(candidate.score==old.score&&
            candidate.model.length()>old.model.length()))
            found.put(key,candidate);
    }
}
