package mk.seteczaliha.app;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Independent indexed web search when Google blocks WebView or automated HTML.
 * Every result is verified against the exact model before it reaches the UI.
 */
final class AlternativeSearchFetcher {
    private AlternativeSearchFetcher(){}

    static List<GoogleModelResearch.Result> search(String model){
        List<GoogleModelResearch.Result> result=new ArrayList<>();
        String code=SetecModelMatcher.modelCode(model);
        String query=code.length()>=5
            ?("\""+code+"\" "+model.trim().split("\\s+")[0]+" specifications")
            :GoogleModelResearch.searchQuery(model);
        String encoded="";
        try{encoded=URLEncoder.encode(query,"UTF-8");}catch(Exception e){return result;}
        String[] urls={
            "https://www.bing.com/search?q="+encoded,
            "https://html.duckduckgo.com/html/?q="+encoded
        };
        Set<String> visited=new HashSet<>();
        for(int provider=0;provider<urls.length;provider++){
            try{
                Document document=Jsoup.connect(urls[provider])
                    .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept-Language","en-US,en;q=0.7")
                    .referrer(provider==0?"https://www.bing.com/":"https://duckduckgo.com/")
                    .followRedirects(true).timeout(5500).maxBodySize(900000).get();
                for(GoogleModelResearch.Result hit:parse(document.html(),model,provider==0?"bing":"ddg")){
                    if(visited.add(hit.url))result.add(hit);
                    if(result.size()>=7)return result;
                }
                if(!result.isEmpty())break;
            }catch(Exception ignored){/* Different provider may still work. */}
        }
        return result;
    }

    static List<GoogleModelResearch.Result> parse(String html,String model,String provider){
        List<GoogleModelResearch.Result> result=new ArrayList<>();
        if(html==null||html.length()<10)return result;
        Document doc=Jsoup.parse(html,provider.equals("bing")?"https://www.bing.com":"https://html.duckduckgo.com");
        Set<String> urls=new HashSet<>();
        for(Element box:doc.select(provider.equals("bing")?"li.b_algo":".result")){
            Element a=box.selectFirst(provider.equals("bing")?"h2 a[href]":"a.result__a[href]");
            if(a==null)continue;
            String link=sourceUrl(a.absUrl("href"));
            if(link.isEmpty()||!urls.add(link))continue;
            String title=a.text().trim();
            Element snippet=box.selectFirst(provider.equals("bing")?".b_caption p, .b_snippet":".result__snippet");
            String description=snippet==null?"":snippet.text().trim();
            if(title.isEmpty())continue;
            GoogleModelResearch.Result hit=new GoogleModelResearch.Result(
                title,description.length()>650?description.substring(0,650):description,link);
            if(GoogleModelResearch.relevant(model,hit))result.add(hit);
            if(result.size()>=8)break;
        }
        return result;
    }

    private static String sourceUrl(String raw){
        try{
            if(raw==null||raw.isEmpty())return "";
            if(raw.startsWith("//"))raw="https:"+raw;
            URI uri=new URI(raw);
            String host=uri.getHost();
            if(host==null)return "";
            if(host.toLowerCase(Locale.ROOT).contains("duckduckgo.com")){
                String query=uri.getRawQuery();
                if(query==null)return "";
                for(String part:query.split("&")){
                    if(part.startsWith("uddg=")){
                        uri=new URI(URLDecoder.decode(part.substring(5),"UTF-8"));
                        host=uri.getHost();
                        break;
                    }
                }
            }
            if(host==null||!"https".equalsIgnoreCase(uri.getScheme()))return "";
            String h=host.toLowerCase(Locale.ROOT);
            if(h.equals("bing.com")||h.endsWith(".bing.com")||
               h.equals("duckduckgo.com")||h.endsWith(".duckduckgo.com")||
               h.equals("google.com")||h.endsWith(".google.com"))return "";
            return uri.toString();
        }catch(Exception e){return "";}
    }
}
