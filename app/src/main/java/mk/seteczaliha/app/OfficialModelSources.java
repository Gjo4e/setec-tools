package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * An independent manufacturer source is useful when Google blocks automatic
 * search snippets. Only exact model pages are accepted; NO Setec descriptions.
 * Other models still use live Google/independent search.
 */
final class OfficialModelSources {
    private OfficialModelSources() {}

    private static String trustedPage(String model) {
        String code=SetecModelMatcher.modelCode(model);
        // Grounded manufacturer product address, verified for this exact model.
        if("WNHVB72SDS".equals(code))
            return "https://mk.gorenje.com/proizvodi/perenje-i-susenje-alista/masini-za-perenje-alista/WASHER-WFVB7012M-WNHVB72SDS-GOR/p/000000000020010128";
        return "";
    }

    static List<GoogleModelResearch.Result> fetch(String model) {
        List<GoogleModelResearch.Result> found=new ArrayList<>();
        String url=trustedPage(model);
        if(url.isEmpty())return found;
        try {
            Document doc=Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/123 Mobile Safari/537.36")
                .header("Accept-Language","mk-MK,mk;q=0.9,en;q=0.7")
                .followRedirects(true).timeout(6800).maxBodySize(1100000).get();
            GoogleModelResearch.Result result=parsePage(doc,model,url);
            if(result!=null)found.add(result);
        }catch(Exception ignored) { /* Search fallback will still run. */ }
        return found;
    }

    static GoogleModelResearch.Result parsePage(Document doc,String model,String url) {
        if(doc==null||url==null||!url.startsWith("https://"))return null;
        String code=SetecModelMatcher.modelCode(model);
        if(code.length()<5)return null;
        // Confirm the identity in the PRODUCT TITLE, not only in related items.
        String identity=doc.title()+" "+
            doc.select("h1,h2,meta[property=og:title]").eachText().toString();
        String normalized=identity.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
        if(!normalized.contains(code))return null;

        Element main=doc.selectFirst("main");
        String body=(main==null?doc.body():main).text();
        if(body.isEmpty())return null;
        StringBuilder details=new StringBuilder();
        String headline=doc.select("h1").text().replaceAll("\\s+"," ").trim();
        if(!headline.isEmpty())details.append(headline.substring(0,Math.min(145,headline.length()))).append(". ");
        append(details,"Капацитет за перење",
            find(body,"(?:Капацитет\\s*за\\s*перење|Washing\\s*capacity|Washing\\s*load)\\s*:?\\s*(\\d{1,2}(?:[.,]\\d+)?\\s*(?:kg|кг))"));
        append(details,"Брзина на центрифуга",
            find(body,"(?:Брзина\\s*на\\s*центрифугирање\\s*(?:1/1)?|Spin\\s*speed)\\s*:?\\s*(\\d{3,4}\\s*(?:rpm|об/мин))"));
        append(details,"Енергетска класа",
            find(body,"(?:Енергетска\\s*класа|Energy\\s*class)\\s*:?\\s*([A-G](?:\\+\\+?)?)\\b"));
        append(details,"Програми",
            find(body,"(?:Број\\s*на\\s*програми|Number\\s*of\\s*programs)\\s*:?\\s*(\\d{1,2})\\b"));
        append(details,"Потрошувачка на вода",
            find(body,"(?:Потрошувачка\\s*на\\s*вода\\s*по\\s*циклус[^0-9]{0,40})\\s*:?\\s*(\\d{1,3}\\s*(?:l|л))\\b"));
        for(String feature:new String[]{"SteamTech","DelayEnd","DrumClean","SnowFlake"}){
            if(body.toLowerCase(Locale.ROOT).contains(feature.toLowerCase(Locale.ROOT))){
                if(details.indexOf("Особини:")<0)details.append("Особини: ");
                else details.append(", ");
                details.append(feature);
            }
        }
        String summary=details.toString().trim();
        if(summary.length()<25)return null;
        return new GoogleModelResearch.Result(doc.title(),summary,url);
    }

    private static String find(String text,String regexp){
        Matcher m=Pattern.compile(regexp,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text);
        return m.find()?m.group(1).trim():"";
    }
    private static void append(StringBuilder out,String label,String value) {
        if(!value.isEmpty())out.append(label).append(": ").append(value).append(". ");
    }
}
