package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strictly Setec-only, NON-AI recognizer for important technical information.
 * Check technical tab, short description AND long H1 title, all from the same
 * confirmed product page. Never invent missing values or scrape recommended
 * products, prices, marketing, installments or warranty.
 */
final class SetecKeySpecs {
    private SetecKeySpecs(){}

    private static final String STOP =
        "(?iu)^(?:Сетек\\s+С[èе]\\s+од\\s+Техника|Слични\\s+производи|Поврзани\\s+производи|"+
        "Препорачани|Рецензии|Оцени|Поврзано|Клуб\\s*цена|Редовна\\s*цена|"+
        "Cashback|Гаранција|Шифра|Достава|Кредит|Рати|Купи|Во\\s+кошничка|"+
        "Контакт|Претплатете|Поставки\\s+за\\s+колачиња)(?:\\s|\\b|:).*";
    private static final Pattern BLOCKED = Pattern.compile(STOP);
    private static final Pattern SALES = Pattern.compile(
        "(?iu)(?:\\b(?:ден(?:ари)?|мкд|mkd|cashback|достава|гаранција|рати|кошничка|"+
        "купете|клуб\\s*цена|редовна\\s*цена|попуст|поени)\\b|\\d+[.,]\\d{3}\\s*ден)");
    private static final Pattern SPLIT = Pattern.compile(
        "(?iu)\\s+[-–—|]\\s*(?=(?:процесор|processor|cpu|рам|ram|меморија|"+
        "графичка|graphics|gpu|ssd|hdd|диск|екран|дисплеј|display|screen|"+
        "батерија|резолуција|resolution|капацитет|енергетска|центрифуга|"+
        "димензи(?:и|ја)|programmes|програми)\\b)");
    private static final String SIZE = "(\\d{1,4}(?:[.,]\\d+)?\\s*(?:GB|ГБ|TB|ТБ))";
    private static final String DIM = "(\\d{2,4}(?:[.,]\\d+)?\\s*[x×х]\\s*"+
        "\\d{2,4}(?:[.,]\\d+)?\\s*[x×х]\\s*\\d{2,4}(?:[.,]\\d+)?\\s*(?:cm|mm|см|мм)?)";
    private static final String SCREEN = "(\\d{1,2}(?:[.,]\\d)?\\s*(?:\"|”|″|inch|инч(?:и)?))";

    static List<GoogleModelResearch.Fact> extract(String model,String url,String specs){
        return extract(model,url,specs,"","");
    }

    /** Preserve source priority: technical tab > short product description > H1. */
    static List<GoogleModelResearch.Fact> extract(
            String model,String url,String specs,String shortDescription,String fullTitle){
        List<GoogleModelResearch.Fact> out=new ArrayList<>();
        if(!SetecPageData.isOfficialProductUrl(url))return out;
        String classification=(String.valueOf(model)+" "+String.valueOf(fullTitle)).toUpperCase(Locale.ROOT);
        boolean laptop=has(classification,"ЛАПТОП","NOTEBOOK","MACBOOK","ASPIRE",
            "VIVOBOOK","ZENBOOK","IDEAPAD","THINKPAD","LATITUDE","PAVILION",
            "PROBOOK","ELITEBOOK","NITRO","LEGION","INSPIRON","ACER ASPIRE");
        boolean washer=has(classification,"ПЕРЕЊЕ","ПЕРАЛНА","WASHING","WASHER",
            "ПРАЛНА","WNH","TWM","MF110W","WW8","WW9","WF8","WF9");
        boolean tv=has(classification,"ТЕЛЕВИЗОР","SMART TV"," QLED"," OLED",
            "ТВ ","TELEVISION","MONITOR","МОНИТОР");
        boolean fridge=has(classification,"ФРИЖИДЕР","ЗАМРЗНУВАЧ","REFRIGERATOR",
            "FRIDGE","FREEZER");
        boolean phone=has(classification,"ТЕЛЕФОН","СМАРТФОН","PHONE","GALAXY S",
            "IPHONE","REDMI","POCO","TABLET","ТАБЛЕТ");
        Map<String,String> found=new LinkedHashMap<>();

        for(String part:new String[]{specs,shortDescription,fullTitle}){
            if(part==null||part.trim().isEmpty())continue;
            String text=part.replace('\u00a0',' ').replace('•','\n')
                .replace('·','\n').replace('\t','\n').replace('\r','\n');
            text=SPLIT.matcher(text).replaceAll("\n");
            String[] lines=text.split("\\n");
            int checked=0;
            for(String raw:lines){
                if(checked++>=100)break;
                String line=raw.replaceAll("\\s+"," ").trim()
                    .replaceFirst("^[-–—:;]+\\s*","");
                if(line.length()<3)continue;
                if(BLOCKED.matcher(line).matches())break;
                if(line.length()>260||SALES.matcher(line).find())continue;
                if(line.toLowerCase(Locale.ROOT).startsWith("технички спецификации"))continue;
                parse(line,found,laptop,washer,tv,fridge,phone);
            }
        }
        List<String> priority;
        if(laptop)priority=Arrays.asList("Процесор","RAM","Диск","Графичка","Екран");
        else if(washer)priority=Arrays.asList("Капацитет на перење","Центрифуга",
            "Енергетска класа","Програми","Димензии");
        else if(tv)priority=Arrays.asList("Дијагонала","Резолуција",
            "Освежување","Технологија на екран","Smart TV");
        else if(fridge)priority=Arrays.asList("Капацитет","Енергетска класа",
            "No Frost","Димензии","Бучава");
        else if(phone)priority=Arrays.asList("Екран","RAM","Диск","Батерија","Камера");
        else priority=Arrays.asList("RAM","Диск","Капацитет","Екран",
            "Резолуција","Енергетска класа","Димензии","Програми","Бучава");
        for(String key:priority){
            String value=found.get(key);
            if(value!=null&&!value.isEmpty()&&out.size()<5)
                out.add(new GoogleModelResearch.Fact(key,value,url));
        }
        return out;
    }

    private static void parse(String line,Map<String,String> facts,
            boolean laptop,boolean washer,boolean tv,boolean fridge,boolean phone){
        if(laptop){
            offer(facts,"Процесор",line,
                "(?iu)(?:^|\\s)(?:процесор|processor|cpu)\\s*[:\\-]?\\s*"+
                "((?:AMD|Intel|Apple|Ryzen|Core|Snapdragon|MediaTek|Qualcomm)[^;|\\n]{3,100})");
            offer(facts,"Графичка",line,
                "(?iu)(?:^|\\s)(?:графичка(?:\\s*карта)?|graphics|gpu|video\\s*card)\\s*[:\\-]?\\s*(.{4,90})");
        }
        if(laptop||phone||!washer&&!tv&&!fridge){
            offer(facts,"RAM",line,
                "(?iu)(?:^|\\s)(?:рам|ram|memory|меморија)(?:\\s*меморија)?"+
                "\\s*[:\\-]?\\s*(\\d{1,3}\\s*(?:GB|ГБ)(?:\\s*(?:LP?DDR[345](?:X)?)(?:\\s*\\d{3,5}\\s*MHz)?)?)");
            offer(facts,"RAM",line,
                "(?iu)\\b(\\d{1,3}\\s*(?:GB|ГБ)(?:\\s*(?:LP?DDR[345](?:X)?))?)\\s*(?:RAM|РАМ)\\b");
            offer(facts,"RAM",line,
                "(?iu)\\b(\\d{1,3}\\s*(?:GB|ГБ)\\s*(?:LP?DDR[345](?:X)?))\\b");
            offer(facts,"Диск",line,
                "(?iu)(?:^|\\s)(?:ssd|hdd|диск|storage|диск\\s*меморија|"+
                "внатрешна\\s*меморија)\\s*[:\\-]?\\s*"+
                SIZE+"(?:\\s*(?:SSD|HDD|NVMe|PCIe|M\\.2))?");
            offer(facts,"Диск",line,
                "(?iu)\\b"+SIZE+"\\s*(?:PCIe|NVMe|M\\.2|SSD|HDD)\\b");
            // Common Setec spec styles: "16GB/512GB SSD", "8 GB RAM, 256 GB SSD".
            offer(facts,"Диск",line,
                "(?iu)\\b\\d{1,3}\\s*(?:GB|ГБ)\\s*[/+]\\s*"+
                SIZE+"\\b");
        }
        if(laptop||phone){
            offer(facts,"Екран",line,
                "(?iu)(?:^|\\s)(?:екран|screen|display|дисплеј)\\s*[:\\-]?\\s*"+
                "("+SCREEN+"[^;|\\n]{0,45})");
            offer(facts,"Екран",line,
                "(?iu)\\b("+SCREEN+"\\s*[^;|\\n]{0,55}(?:FHD|HD|Full\\s*HD|"+
                "IPS|OLED|AMOLED|1920|2560|2880|1080|1440)[^;|\\n]{0,18})");
            // If a labelled display is given without inches, keep a useful value.
            offer(facts,"Екран",line,
                "(?iu)^(?:екран|дисплеј|screen|display)\\s*[:\\-]\\s*(.{5,70})");
        }
        if(phone){
            offer(facts,"Батерија",line,
                "(?iu)(?:батерија|battery)\\s*[:\\-]?\\s*(\\d{3,5}\\s*mAh)");
            offer(facts,"Камера",line,
                "(?iu)(?:камера|camera)\\s*[:\\-]?\\s*(\\d{1,3}\\s*MP\\b[^;|]{0,20})");
        }
        if(washer){
            offer(facts,"Капацитет на перење",line,
                "(?iu)(?:капацитет(?:\\s*на\\s*перење)?|полнење|washing\\s*capacity)"+
                "\\s*[:\\-]?\\s*(\\d{1,2}(?:[.,]\\d+)?\\s*(?:kg|кг))");
            offer(facts,"Капацитет на перење",line,
                "(?iu)\\b(\\d{1,2}(?:[.,]\\d+)?\\s*(?:kg|кг))\\b");
            offer(facts,"Центрифуга",line,
                "(?iu)(?:центрифуга|брзина\\s*на\\s*центрифуга|spin\\s*speed)"+
                "\\s*[:\\-]?\\s*(\\d{3,4}\\s*(?:rpm|врт(?:ежи)?(?:/мин)?|обр(?:/мин)?))");
            offer(facts,"Центрифуга",line,
                "(?iu)(\\d{3,4}\\s*(?:rpm|вртежи|врт/мин|обр/мин))");
            offer(facts,"Програми",line,
                "(?iu)(?:број\\s*на\\s*програми|програми|programmes|programs)\\s*[:\\-]?\\s*(\\d{1,2})\\b");
            offer(facts,"Програми",line,
                "(?iu)\\b(\\d{1,2})\\s*(?:програми|programs|programmes)\\b");
        }
        if(fridge||!laptop&&!washer&&!tv&&!phone){
            offer(facts,"Капацитет",line,
                "(?iu)(?:капацитет|вкупен\\s*волумен|volum[e]?|capacity)"+
                "\\s*[:\\-]?\\s*(\\d{1,4}\\s*(?:l|L|литри|л))\\b");
            if(fridge)offer(facts,"Капацитет",line,
                "(?iu)\\b(\\d{2,4}\\s*(?:L|л|литри))\\b");
        }
        if(fridge&&Pattern.compile("(?iu)\\bno\\s*frost\\b").matcher(line).find())
            facts.putIfAbsent("No Frost","Да");
        if(tv){
            offer(facts,"Дијагонала",line,
                "(?iu)(?:дијагонала|големина\\s*на\\s*екран|screen\\s*size)"+
                "\\s*[:\\-]?\\s*"+SCREEN);
            offer(facts,"Дијагонала",line,"(?iu)\\b"+SCREEN);
            offer(facts,"Освежување",line,
                "(?iu)(?:освежување|refresh\\s*rate|фреквенција)\\s*[:\\-]?\\s*(\\d{2,3}\\s*Hz)");
            offer(facts,"Технологија на екран",line,
                "(?iu)(?:^|\\s)(OLED|QLED|Mini[- ]LED|LED)\\b");
            if(Pattern.compile("(?iu)\\bsmart\\s*tv\\b").matcher(line).find())
                facts.putIfAbsent("Smart TV","Да");
        }
        if(tv||!laptop&&!washer&&!fridge){
            offer(facts,"Резолуција",line,
                "(?iu)(?:резолуција|resolution)\\s*[:\\-]?\\s*(\\d{3,4}\\s*[x×]\\s*\\d{3,4})");
            if(tv)offer(facts,"Резолуција",line,
                "(?iu)\\b(3840\\s*[x×]\\s*2160|1920\\s*[x×]\\s*1080|4K\\s*UHD)\\b");
        }
        if(washer||fridge||!laptop&&!phone&&!tv){
            offer(facts,"Енергетска класа",line,
                "(?iu)(?:енергетска(?:\\s*класа|\\s*ефикасност)?|energy\\s*(?:class|rating))"+
                "\\s*[:\\-]?\\s*([A-GА-Г]\\+{0,3})(?=\\s|$|[,;])");
            offer(facts,"Енергетска класа",line,
                "(?iu)\\b([A-GА-Г]\\+{0,3})\\s*(?:енергетска\\s*класа|energy\\s*class)\\b");
            offer(facts,"Димензии",line,
                "(?iu)(?:димензии|dimensions|големина)\\s*[:\\-]?\\s*"+DIM);
            offer(facts,"Бучава",line,
                "(?iu)(?:бучава|noise|звучна\\s*моќ)\\s*[:\\-]?\\s*(\\d{2,3}\\s*dB)\\b");
        }
    }

    /** Strongly typed values only: model variants or unsupported claims never guessed. */
    private static void offer(Map<String,String> values,String key,String line,String regex){
        if(values.containsKey(key))return;
        Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(line);
        if(!m.find())return;
        String value=m.group(1);
        if(value==null)return;
        value=value.trim().replaceAll("(?iu)\\s+(?:[-–—|,;/]\\s*)?"+
            "(?:RAM|РАМ|SSD|HDD|Графичка|Процесор|CPU|Екран|"+
            "Display|Клуб\\s*цена|Гаранција)\\b.*$","")
            .replaceAll("[,;: .\\-–—|]+$","").trim();
        if(value.isEmpty()||value.length()>100||SALES.matcher(value).find())return;
        if("Процесор".equals(key)){
            value=value.replaceAll("\\s*\\([^)]{0,40}$","").trim();
            if(value.length()<5)return;
        }
        if("RAM".equals(key)&&!value.matches("(?iu).*\\d\\s*(?:GB|ГБ).*"))return;
        if("Диск".equals(key)&&!value.matches("(?iu).*\\d\\s*(?:GB|ГБ|TB|ТБ).*"))return;
        if("Енергетска класа".equals(key)){
            value=value.toUpperCase(Locale.ROOT)
                .replace('А','A').replace('В','B').replace('С','C');
        }
        values.put(key,value);
    }

    private static boolean has(String target,String... tokens){
        for(String token:tokens)if(target.contains(token))return true;
        return false;
    }
}
