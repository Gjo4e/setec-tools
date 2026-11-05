package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Require strong model/variant agreement before showing Setec product data. */
final class SetecModelMatcher {
    private static final Set<String> COLORS = new LinkedHashSet<>(Arrays.asList(
        "INDIGO","SILVER","CITRUS","BLUSH","BLACK","WHITE","BLUE","GREEN",
        "GRAY","GREY","GOLD","RED","PINK","MIDNIGHT","STARLIGHT"));
    private static final Set<String> GENERIC = new LinkedHashSet<>(Arrays.asList(
        "LAPTOP","NOTEBOOK","PRODUCT","MODEL","PC","DESKTOP","TABLET",
        "ЛАПТОП","НОУТБУК","ТАБЛЕТ","КОМПЈУТЕР","ПРОИЗВОД","МОДЕЛ"));

    private SetecModelMatcher() {}

    static String clean(String value) {
        String text = ModelNameParser.normalizeModel(value == null ? "" : value);
        text = text.toUpperCase(Locale.ROOT).replaceAll("[^\\p{L}\\d]+", " ").trim();
        return text.replaceAll("\\s+", " ");
    }

    static String searchTerm(String query){
        String name=ModelNameParser.normalizeModel(query);
        String upper=name.toUpperCase(Locale.ROOT);
        // Broad searches find product variants; strict color/model matching happens later.
        if(upper.contains("MACBOOK")){
            String[] t=upper.split("\\s+");
            int idx=Arrays.asList(t).indexOf("MACBOOK");
            if(idx>=0&&idx+1<t.length)return "MacBook "+t[idx+1];
            return "MacBook";
        }
        String candidate=name.replaceAll("(?i)\\b(INDIGO|SILVER|CITRUS|BLUSH|BLACK|WHITE|GRAY|BLUE)\\b","").trim();
        return candidate.length()>72?candidate.substring(0,72).trim():candidate;
    }

    static int score(String query,String productName){
        String q=clean(query),p=clean(productName);
        if(q.length()<4||p.length()<4)return -1;
        Set<String> queryTokens=new LinkedHashSet<>(Arrays.asList(q.split(" ")));
        Set<String> productTokens=new LinkedHashSet<>(Arrays.asList(p.split(" ")));
        queryTokens.removeAll(GENERIC);productTokens.removeAll(GENERIC);
        if(queryTokens.size()<2)return -1;
        for(String color:COLORS)
            if(queryTokens.contains(color)&&!productTokens.contains(color))return -1;
        int matched=0,significant=0,numeric=0;
        for(String token:queryTokens){
            if(token.length()<2)continue;
            significant++;
            if(productTokens.contains(token)){matched++;if(token.matches(".*\\d.*"))numeric++;}
            else if(token.matches(".*\\d.*"))return -1; // Different capacity/model/size.
        }
        if(significant==0||matched<2)return -1;
        int score=matched*100/significant;
        if(matched==significant)score+=10;
        if(numeric>0)score+=5;
        return score>=80?score:-1;
    }
}
