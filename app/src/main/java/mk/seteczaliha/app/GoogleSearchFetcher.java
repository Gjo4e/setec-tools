package mk.seteczaliha.app;

import android.net.Uri;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Independent Google HTML fetch for when the off-screen WebView has no search
 * results (mobile search pages often defer their h3 nodes). No Google API key.
 * When Google blocks automated requests, caller falls back to other public
 * search snippets and says which source was used.
 */
final class GoogleSearchFetcher {
    private GoogleSearchFetcher() {}

    static List<GoogleModelResearch.Result> search(String model) throws Exception {
        String query=GoogleModelResearch.searchQuery(model);
        if(query.trim().isEmpty())return new ArrayList<>();
        String url="https://www.google.com/search?q="+
            java.net.URLEncoder.encode(query,"UTF-8")+"&num=10&hl=en&gbv=1";
        Document doc=Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept-Language","en-US,en;q=0.8")
            .referrer("https://www.google.com/")
            .followRedirects(true)
            .timeout(8500)
            .maxBodySize(850000)
            .get();
        return parseHtml(doc.html(),model);
    }

    static List<GoogleModelResearch.Result> parseHtml(String html,String model) {
        List<GoogleModelResearch.Result> results=new ArrayList<>();
        if(html==null||html.isEmpty())return results;
        Document doc=Jsoup.parse(html,"https://www.google.com");
        Set<String> seen=new HashSet<>();
        for(Element title:doc.select("h3")){
            Element a=title.closest("a[href]");
            if(a==null)continue;
            String href=a.absUrl("href");
            if(href.isEmpty())href=a.attr("href");
            String verified=externalUrl(href);
            if(verified.isEmpty()||!seen.add(verified))continue;
            String heading=title.text().trim();
            if(heading.length()<5||heading.length()>200)continue;

            Element box=title.closest("div.MjjYud,div.g,article");
            if(box==null){
                box=a.parent();
                if(box!=null&&box.parent()!=null)box=box.parent();
            }
            String snippet="";
            if(box!=null){
                Element text=box.selectFirst(".VwiC3b,.IsZvec,.yXK7lf,[data-sncf],.st,.MUxGbd");
                if(text!=null)snippet=text.text();
                if(snippet.isEmpty()){
                    String entire=box.text();
                    if(entire.startsWith(heading))snippet=entire.substring(heading.length()).trim();
                }
            }
            // Google can surface related but different models. Never mix their specs.
            GoogleModelResearch.Result candidate=new GoogleModelResearch.Result(
                heading,snippet.length()>550?snippet.substring(0,550):snippet,verified);
            if(GoogleModelResearch.relevant(model,candidate))results.add(candidate);
            if(results.size()>=8)break;
        }
        return results;
    }

    private static String externalUrl(String value){
        if(value==null||value.trim().isEmpty())return "";
        try{
            Uri u=Uri.parse(value.trim());
            if(value.startsWith("/url?"))u=Uri.parse("https://www.google.com"+value);
            String host=u.getHost();
            if(host!=null&&host.toLowerCase().endsWith("google.com")&&"/url".equals(u.getPath())){
                String target=u.getQueryParameter("q");
                if(target==null||target.isEmpty())target=u.getQueryParameter("url");
                if(target==null)return "";
                u=Uri.parse(target);
                host=u.getHost();
            }
            if(!"https".equalsIgnoreCase(u.getScheme())||host==null||host.isEmpty())return "";
            String lower=host.toLowerCase();
            if(lower.equals("google.com")||lower.endsWith(".google.com")||
                lower.equals("googleusercontent.com")||lower.endsWith(".googleusercontent.com")||
                lower.contains("youtube.com")||lower.equals("youtu.be"))return "";
            return u.toString();
        }catch(Exception e){return "";}
    }
}
