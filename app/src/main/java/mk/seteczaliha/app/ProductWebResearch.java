package mk.seteczaliha.app;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.Locale;

/** Best-effort public web lookup; source snippets are attributed and never treated as verified facts. */
public final class ProductWebResearch {
    private ProductWebResearch(){}
    public static JSONObject search(String model) {
        JSONObject result=new JSONObject();
        JSONArray sources=new JSONArray();
        try {
            String query=model+" official specifications "+(isPrinter(model)?"toner cartridge page yield ppm":"technical specifications");
            String url="https://www.bing.com/search?format=rss&q="+URLEncoder.encode(query,"UTF-8");
            Document rss=Jsoup.connect(url).userAgent("Mozilla/5.0 (compatible; SetecTools/1.0)")
                .timeout(14000).maxBodySize(350000).get();
            int count=0;
            for(Element item:rss.select("item")) {
                if(count>=6)break;
                String title=item.selectFirst("title")!=null?item.selectFirst("title").text():"";
                String link=item.selectFirst("link")!=null?item.selectFirst("link").text():"";
                String description=item.selectFirst("description")!=null?item.selectFirst("description").text():"";
                if(!link.startsWith("https://")||title.isEmpty())continue;
                JSONObject entry=new JSONObject();
                entry.put("title",title.substring(0,Math.min(180,title.length())));
                entry.put("url",link);
                entry.put("snippet",description.substring(0,Math.min(650,description.length())));
                sources.put(entry);count++;
            }
            result.put("status",count>0?"ok":"empty");
            result.put("sources",sources);
            result.put("model",model);
        } catch(Exception e){
            try{result.put("status","error");result.put("message","Не може да се преземат интернет-резултати. Провери интернет и пробај повторно.");result.put("sources",sources);}catch(Exception ignored){}
        }
        return result;
    }
    private static boolean isPrinter(String n){
        String s=n.toLowerCase(Locale.ROOT);
        return s.matches(".*(принтер|печатач|printer|laserjet|deskjet|officejet|ecotank|pixma).*");
    }
}
