package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Five category-specific technical criteria from verified SETEC facts.
 * Unknown families use a conservative generic order; named families never
 * borrow an unrelated category's specification just to fill five positions.
 */
final class ComparisonSummary {
    static final int MAX_SPECS=5;
    private static final List<String> GENERAL=Arrays.asList(
        "Процесор","RAM","Диск","Графичка","Екран",
        "Капацитет на перење","Капацитет на сушење","Центрифуга",
        "Дијагонала","Резолуција","Освежување",
        "Капацитет BTU","Проток на воздух",
        "Моќност","Капацитет","Програми",
        "Енергетска класа","Бучава","Димензии",
        "Батерија","Камера","No Frost","Брзини");
    private ComparisonSummary(){}

    static List<String> topSpecs(String leftModel,Map<String,String> left,
                                 String rightModel,Map<String,String> right){
        Set<String> available=new LinkedHashSet<>();
        if(left!=null)available.addAll(left.keySet());
        if(right!=null)available.addAll(right.keySet());
        if(available.isEmpty())return new ArrayList<>();
        CompareCategoryGuard.Kind a=CompareCategoryGuard.guess(leftModel,left);
        CompareCategoryGuard.Kind b=CompareCategoryGuard.guess(rightModel,right);
        if(a!=CompareCategoryGuard.Kind.UNKNOWN&&b!=CompareCategoryGuard.Kind.UNKNOWN&&a!=b)
            return new ArrayList<>();
        CompareCategoryGuard.Kind kind=a!=CompareCategoryGuard.Kind.UNKNOWN?a:b;
        List<String> priorities=CompareCategoryProfiles.known(kind)
            ?CompareCategoryProfiles.criteria(kind):GENERAL;
        List<String> result=new ArrayList<>();
        for(String key:priorities){
            if(available.contains(key)&&result.size()<MAX_SPECS)result.add(key);
        }
        // Unclassified items may still carry uncommon valid named Setec facts.
        // For a KNOWN family, however, only its defined criteria are eligible.
        if(kind==CompareCategoryGuard.Kind.UNKNOWN&&result.size()<MAX_SPECS){
            for(String key:available){
                if(key!=null&&!key.trim().isEmpty()&&!result.contains(key)){
                    result.add(key);
                    if(result.size()>=MAX_SPECS)break;
                }
            }
        }
        return result;
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
