package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Reads the exact product's details from NON-SETEC product pages found in
 * public web search. Search snippets alone often contain only one attribute.
 * Never read unrelated models or retailer product suggestions as facts.
 */
final class IndependentProductDetails {
    private IndependentProductDetails(){}

    static List<GoogleModelResearch.Result> enrich(
            String model,List<GoogleModelResearch.Result> results) {
        List<GoogleModelResearch.Result> enriched=new ArrayList<>();
        Set<String> visited=new HashSet<>();
        if(results==null)return enriched;
        int pages=0;
        for(GoogleModelResearch.Result result:results){
            if(!GoogleModelResearch.relevant(model,result)||!visited.add(result.url))continue;
            GoogleModelResearch.Result current=result;
            if(pages<3 && GoogleModelResearch.extract(model,
                    java.util.Collections.singletonList(current)).size()<3){
                pages++;
                try {
                    Document doc=Jsoup.connect(result.url)
                        .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/123 Mobile Safari/537.36")
                        .header("Accept-Language","mk-MK,mk;q=0.8,en-US;q=0.7")
                        .followRedirects(true).timeout(4900).maxBodySize(950000).get();
                    GoogleModelResearch.Result fuller=fromPage(model,result,doc);
                    if(fuller!=null)current=fuller;
                }catch(Exception ignored){ /* Retain the verified snippet. */ }
            }
            enriched.add(current);
            if(enriched.size()>=7)break;
        }
        return enriched;
    }

    static GoogleModelResearch.Result fromHtml(
            String model,GoogleModelResearch.Result result,String html) {
        if(html==null||html.isEmpty())return null;
        return fromPage(model,result,Jsoup.parse(html,result.url));
    }

    private static GoogleModelResearch.Result fromPage(
            String model,GoogleModelResearch.Result result,Document document) {
        if(GoogleModelResearch.blockedSource(result.url)||document==null)return null;
        String heading=document.select("h1").text().trim();
        String pageTitle=document.title().trim();
        // Validate the page product identity, not just the search result snippet.
        GoogleModelResearch.Result identity=new GoogleModelResearch.Result(
            (heading.isEmpty()?pageTitle:heading)+" "+pageTitle,"",result.url);
        if(!GoogleModelResearch.relevant(model,identity))return null;
        StringBuilder content=new StringBuilder(result.snippet).append(" . ");
        String description=document.select("meta[name=description],meta[property=og:description]")
            .stream().map(x->x.attr("content")).filter(x->!x.isEmpty()).findFirst().orElse("");
        if(!description.isEmpty())content.append(description).append(" . ");
        Element detail=document.selectFirst(
            ".woocommerce-product-details__short-description,"
           +".product-description,.product__description,.product-detail-description,"
           +"[itemprop=description],#tab-description,.product-summary,"
           +".product-content,.product-description-content,.description-content");
        if(detail==null)detail=document.selectFirst("main");
        if(detail==null)detail=document.body();
        if(detail!=null){
            Element clone=detail.clone();
            clone.select("script,style,nav,footer,header,aside,form,"
                +".related,.related-products,.recommendations,[class*=recommend],"
                +"[class*=cross-sell],[class*=upsell]").remove();
            String text=clone.text().replaceAll("\\s+"," ").trim();
            if(text.length()>7000)text=text.substring(0,7000);
            content.append(text);
        }
        String text=content.toString().replaceAll("\\s+"," ").trim();
        // Ensure that the extracted text offers meaningful extra content.
        if(text.length()<=result.snippet.length()+20)return null;
        return new GoogleModelResearch.Result(
            result.title,text.length()>8000?text.substring(0,8000):text,result.url);
    }
}
