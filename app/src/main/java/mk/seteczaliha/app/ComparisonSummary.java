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
        "Капацитет","Резолуција","Димензии","Бучава","Батерија","Камера");
    private static final List<String> APPLIANCE = Arrays.asList(
        "Капацитет","Енергетска класа","No Frost","Димензии","Бучава");
    private static final List<String> TV = Arrays.asList(
        "Дијагонала","Резолуција","Освежување","Технологија на екран",
        "Smart TV","Оперативен систем","HDR","HDMI");
    private static final List<String> TOASTER=Arrays.asList(
        "Моќност","Број на парчиња","Степени на печење","Плочи",
        "Одмрзнување","Тацна за трошки","Автоматско исклучување","Термостат");
    private static final List<String> HAIR_DRYER=Arrays.asList(
        "Моќност","Брзини","Нивоа на топлина","Јонизатор",
        "Ладен воздух","Дифузер");
    private static final List<String> HOOD=Arrays.asList(
        "Проток на воздух","Брзини","Бучава","Ширина","Филтер","Осветлување");
    private static final List<String> AC=Arrays.asList(
        "Капацитет BTU","Ладење","Греење","Енергетска класа",
        "SEER","SCOP","Wi-Fi","Работна температура");
    private static final List<String> SMALL_APPLIANCE=Arrays.asList(
        "Моќност","Капацитет","Брзини","Број на вртежи","Програми",
        "Степени на печење","Нивоа на топлина","Температура","Автономија",
        "Филтер","Пулс","Тајмер","Автоматско исклучување");
    private static final List<String> GENERAL = Arrays.asList(
        "Моќност","Процесор","RAM","Диск","Графичка","Екран",
        "Капацитет на перење","Центрифуга","Капацитет",
        "Енергетска класа","Програми","Брзини","Дијагонала","Резолуција",
        "Освежување","Димензии","Бучава","No Frost",
        "Капацитет BTU","Ладење","Греење","Проток на воздух",
        "Број на парчиња","Степени на печење","Нивоа на топлина",
        "Температура","Батерија","Камера");

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
        if(available.contains("Број на парчиња")||
            available.contains("Степени на печење")||
            available.contains("Тацна за трошки")||
            models.contains("ТОСТЕР")||models.contains("TOСТЕР"))return TOASTER;
        if(available.contains("Јонизатор")||
            available.contains("Нивоа на топлина")||
            models.contains("ФЕН ЗА КОСА"))return HAIR_DRYER;
        if(available.contains("Капацитет BTU")||
            available.contains("SEER")||available.contains("SCOP")||
            models.contains("КЛИМА"))return AC;
        if(available.contains("Проток на воздух")&&
            (available.contains("Брзини")||available.contains("Филтер"))||
            models.contains("АСПИРАТОР"))return HOOD;
        if(available.contains("Моќност")||
            available.contains("Број на вртежи")||
            available.contains("Автоматско исклучување"))
            return SMALL_APPLIANCE;
        if(available.contains("Капацитет на перење")||
            models.contains("WASHING")||models.contains("ПЕРЕЊЕ")||
            models.matches(".*\\b(?:TWM|MF110W|WN[A-Z]{2}\\d)\\b.*"))return WASHER;
        if((available.contains("Дијагонала") &&
            (available.contains("Технологија на екран")||
             available.contains("Smart TV")||
             available.contains("Оперативен систем")))||
            models.contains("ТЕЛЕВИЗОР")||models.contains("TV ")||
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
