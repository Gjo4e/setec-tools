package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turn publicly visible search snippets into a SHORT, cited specification
 * summary. This is not AI and never guesses values: every fact must occur
 * literally in a source snippet about the exact model.
 */
final class GoogleModelResearch {
    static final class Result {
        final String title, snippet, url;
        Result(String title, String snippet, String url) {
            this.title=title==null?"":title.trim();
            this.snippet=snippet==null?"":snippet.trim();
            this.url=url==null?"":url.trim();
        }
    }
    static final class Fact {
        final String label, value, url;
        Fact(String label, String value, String url){
            this.label=label;this.value=value;this.url=url;
        }
    }
    private static final class FactPattern {
        final String label;
        final Pattern regex;
        FactPattern(String label,String regex){
            this.label=label;
            this.regex=Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
        }
    }
    private static final FactPattern[] FACTS = {
        new FactPattern("Капацитет на перење", "(?:washing\\s*capacity|wash\\s*load|load\\s*capacity|kapacitet\\s*(?:za\\s*)?pranja|капацитет\\s*(?:за|на)\\s*перење|washing\\s*load)[^\\n.;]{0,35}?(\\d{1,2}(?:[.,]\\d+)?)\\s*(?:kg|кг)"),
        new FactPattern("Центрифуга", "(?:spin\\s*speed|maximum\\s*spin|centrifug\\w*|центрифуг\\w*|брзина\\s*на\\s*центрифуг\\w*|обртаи|obrata)[^\\n.;]{0,35}?(\\d{3,4})\\s*(?:rpm|r/min|vrt|врт|обр|\\b)"),
        new FactPattern("Програми", "(?:\\b(\\d{1,2})\\s*(?:programmes|programs|programi|програми)\\b)|(?:(?:programmes|programs|programi|програми)\\s*:?\\s*(\\d{1,2})\\b)"),
        new FactPattern("Енергетска класа", "(?:energy\\s*(?:efficiency\\s*)?(?:class|rating)|енергетска\\s*(?:класа|ефикасност)|energetska\\s*klasa)\\s*[:\\-]?\\s*(A\\+\\+\\+|A\\+\\+|A\\+|[A-G])\\b"),
        new FactPattern("Димензии", "(?:dimensions|dimenzije|dimenzii|димензии|size\\s*\\(\\s*W\\s*[x×]\\s*H)[^\\n.;]{0,65}?(\\d{2,3}(?:[.,]\\d+)?\\s*[x×]\\s*\\d{2,3}(?:[.,]\\d+)?\\s*[x×]\\s*\\d{2,3}(?:[.,]\\d+)?\\s*(?:cm|mm)?)"),
        new FactPattern("Бучава", "(?:noise\\s*(?:level)?|buka|бучава)[^\\n.;]{0,25}?([3-9]\\d)\\s*dB\\b"),
        new FactPattern("Капацитет", "(?:storage\\s*capacity|capacity|kapacitet|капацитет)[^\\n.;]{0,22}?(\\d+(?:[.,]\\d+)?)\\s*(GB|TB|l|литри|litres|liters)\\b"),
        new FactPattern("RAM", "(?:RAM|memory|меморија)[^\\n.;]{0,20}?(\\d{1,3})\\s*(GB)\\b"),
        new FactPattern("Резолуција", "(?:resolution|резолуција)[^\\n.;]{0,20}?(\\d{3,4}\\s*[x×]\\s*\\d{3,4})\\b")
    };
    private GoogleModelResearch(){}

    static String searchQuery(String model){
        String normalized=ModelNameParser.normalizeModel(model);
        String code=SetecModelMatcher.modelCode(normalized);
        if(code.length()>=5) {
            String[] words=normalized.toUpperCase(Locale.ROOT).split("\\s+");
            String brand=words.length>0?words[0]:"";
            return (brand+" "+code+" technical specifications").trim();
        }
        return normalized+" technical specifications";
    }

    static boolean relevant(String model,Result result){
        if(result==null||result.title.isEmpty())return false;
        String normalized=ModelNameParser.normalizeModel(model);
        String expectedCode=SetecModelMatcher.modelCode(normalized);
        if(expectedCode.length()>=5) {
            // A matching search snippet does not make an unrelated search RESULT
            // relevant. Require the exact model identity in the page title or URL.
            // This prevents random TikTok / Reddit results for a washing machine.
            String heading=(result.title+" "+result.url).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]","");
            if(!heading.contains(expectedCode))return false;
            String brand=normalized.split("\\s+")[0].toUpperCase(Locale.ROOT);
            String title=result.title.toUpperCase(Locale.ROOT);
            String url=result.url.toUpperCase(Locale.ROOT);
            if(brand.length()>2&&!title.contains(brand)&&!url.contains(brand))return false;
            return true;
        }
        return SetecModelMatcher.score(normalized,result.title)>=80;
    }

    /** Short, attributed search-description excerpt; never generated or guessed. */
    static Result description(String model,List<Result> results){
        if(results==null)return null;
        Result first=null;
        for(Result r:results){
            if(!relevant(model,r)||r.snippet.length()<28||!r.url.startsWith("https://"))continue;
            if(first==null)first=r;
            String host=r.url.toLowerCase(Locale.ROOT);
            // A manufacturer's own pages take priority over retailer blurbs.
            if(host.contains("gorenje.")||host.contains("bosch-home.")||
               host.contains("midea.")||host.contains("samsung.")||
               host.contains("apple.com")||host.contains("asus.com")||
               host.contains("lg.com")||host.contains("whirlpool."))return r;
        }
        return first;
    }

    static List<Fact> extract(String model,List<Result> results){
        Map<String,Fact> facts=new LinkedHashMap<>();
        if(results==null)return new ArrayList<>();
        for(Result r:results){
            if(facts.size()>=7)break;
            if(!relevant(model,r)||!r.url.startsWith("https://"))continue;
            String text=(r.title+" . "+r.snippet).replaceAll("\\s+"," ");
            for(FactPattern spec:FACTS){
                if(facts.containsKey(spec.label))continue;
                Matcher m=spec.regex.matcher(text);
                if(m.find()){
                    String value=m.group(1);
                    if(value==null&&m.groupCount()>=2)value=m.group(2);
                    if(value==null||value.trim().isEmpty())continue;
                    if(spec.label.equals("Капацитет")&&m.groupCount()>=2&&m.group(2)!=null)value+=" "+m.group(2);
                    if(spec.label.equals("Центрифуга"))value+=" rpm";
                    if(spec.label.equals("Капацитет на перење"))value+=" kg";
                    if(spec.label.equals("Бучава"))value+=" dB";
                    if(spec.label.equals("RAM"))value+=" GB";
                    facts.put(spec.label,new Fact(spec.label,value.trim(),r.url));
                    if(facts.size()>=7)break;
                }
            }
        }
        return new ArrayList<>(facts.values());
    }
}
