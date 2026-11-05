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
 * comparison. Values must come from exact-model independently sourced facts.
 * Missing facts are never inferred, extrapolated or invented.
 */
final class ComparisonSummary {
    static final int MAX_SPECS = 5;
    private static final List<String> WASHER = Arrays.asList(
        "Капацитет на перење","Центрифуга","Енергетска класа","Програми","Димензии",
        "Бучава","Заштита од протекување","Одложен почеток");
    private static final List<String> ELECTRONICS = Arrays.asList(
        "RAM","Капацитет","Резолуција","Димензии","Бучава",
        "Енергетска класа","LED дисплеј");
    private static final List<String> APPLIANCE = Arrays.asList(
        "Капацитет","Енергетска класа","Димензии","Бучава","Програми",
        "LED дисплеј","Одложен почеток");
    private static final List<String> GENERAL = Arrays.asList(
        "Капацитет на перење","Центрифуга","RAM","Капацитет",
        "Енергетска класа","Програми","Резолуција","Димензии",
        "Бучава","LED дисплеј","Одложен почеток","Предперење",
        "Заштита од протекување","Пареа");

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
        if(models.contains("FRIDGE")||models.contains("ФРИЖИДЕР")||
            models.contains("REFRIGERATOR")||models.contains("ЗАМРЗНУВАЧ"))return APPLIANCE;
        if(models.contains("MACBOOK")||models.contains("LAPTOP")||
            models.contains("LENOVO")||models.contains("ASUS")||
            available.contains("RAM")||available.contains("Резолуција"))return ELECTRONICS;
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
