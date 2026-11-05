package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Presents the few specifications that matter for a meaningful two-product
 * comparison. Values must come from verified exact-product Setec facts.
 * Missing facts are never inferred, extrapolated or invented.
 */
final class ComparisonSummary {
    static final int MAX_SPECS = 5;
    private static final List<String> WASHER = Arrays.asList(
        "Капацитет на перење","Центрифуга","Енергетска класа","Програми","Димензии",
        "Бучава","Заштита од протекување","Одложен почеток");
    private static final List<String> ELECTRONICS = Arrays.asList(
        "Процесор","RAM","Диск","Графичка","Екран",
        "Резолуција","Батерија","Камера");
    private static final List<String> APPLIANCE = Arrays.asList(
        "Капацитет","Енергетска класа","No Frost","Димензии","Бучава");
    private static final List<String> TV = Arrays.asList(
        "Дијагонала","Резолуција","Освежување","Технологија на екран","Smart TV");
    private static final List<String> GENERAL = Arrays.asList(
        "Процесор","RAM","Диск","Графичка","Екран",
        "Капацитет на перење","Центрифуга","Капацитет",
        "Енергетска класа","Програми","Дијагонала","Резолуција",
        "Освежување","Димензии","Бучава","No Frost");

    private ComparisonSummary(){}

    static List<String> topSpecs(String leftModel, Map<String,String> left,
                                 String rightModel,Map<String,String> right){
        Set<String> available=new LinkedHashSet<>();
        if(left!=null)available.addAll(left.keySet());
        if(right!=null)available.addAll(right.keySet());
        List<String> priorities=priority(leftModel,rightModel,available);
        List<String> result=new ArrayList<>();
        for(String key:priorities){
            if(available.contains(key)&&result.size()<MAX_SPECS)result.add(key);
        }
        if(result.size()<MAX_SPECS){
            for(String key:available)
                if(!result.contains(key)&&result.size()<MAX_SPECS)result.add(key);
        }
        return result;
    }

    private static List<String> priority(String a,String b,Set<String> available){
        String models=(String.valueOf(a)+" "+String.valueOf(b)).toUpperCase();
        if(available.contains("Капацитет на перење")||
            models.contains("WASHING")||models.contains("ПЕРЕЊЕ")||
            models.matches(".*\\b(?:TWM|MF110W|WN[A-Z]{2}\\d)\\b.*"))return WASHER;
        if(models.contains("ТЕЛЕВИЗОР")||models.contains("TV ")||
            models.contains(" QLED")||models.contains(" OLED")||
            models.contains("МОНИТОР")||models.contains("MONITOR"))return TV;
        if(models.contains("FRIDGE")||models.contains("ФРИЖИДЕР")||
            models.contains("REFRIGERATOR")||models.contains("ЗАМРЗНУВАЧ"))return APPLIANCE;
        if(models.contains("MACBOOK")||models.contains("LAPTOP")||
            models.contains("ASPIRE")||models.contains("IDEAPAD")||
            models.contains("VIVOBOOK")||models.contains("THINKPAD")||
            models.contains("LENOVO")||models.contains("ASUS")||
            available.contains("Процесор")||available.contains("Графичка")||
            available.contains("RAM")||available.contains("Диск"))return ELECTRONICS;
        return GENERAL;
    }

    static Map<String,String> asMap(List<GoogleModelResearch.Fact> facts){
        Map<String,String> out=new LinkedHashMap<>();
        if(facts!=null){
            for(GoogleModelResearch.Fact f:facts)
                if(f!=null&&!f.label.isEmpty()&&!f.value.isEmpty())
                    out.put(f.label,f.value);
        }
        return out;
    }
}
