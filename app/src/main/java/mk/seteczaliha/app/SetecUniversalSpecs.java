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
 * Category-aware non-AI Setec specification recognizer.
 * Inputs are ONLY the technical tab, short description and H1 of the exact
 * verified product page. No search providers, no guesses or catalog cards.
 */
final class SetecUniversalSpecs {
    private SetecUniversalSpecs() {}

    private enum Kind {
        TOASTER, HAIR_DRYER, HOOD, AIR_CONDITIONER, DISHWASHER, WASHER,
        CLOTHES_DRYER, FRIDGE, OVEN, HOB, MICROWAVE, VACUUM, BLENDER,
        MIXER, KETTLE, COFFEE, AIR_FRYER, IRON, HAIR_STYLER, FAN,
        HEATER, BOILER, FOOD_PROCESSOR, PRINTER, ROUTER, SPEAKER,
        LAPTOP, PHONE, TV, UNKNOWN
    }
    private static final Pattern STOP=Pattern.compile(
        "(?iu)^(?:Клуб\\s*цена|Редовна\\s*цена|Клуб\\s*кредит|Шифра|Гаранција|"+
        "Cashback|Достава|Поврзани\\s*производи|Слични\\s*производи|"+
        "Препорачани|Плаќање|Оцени|Рецензии|Колачиња|Услови\\s*за\\s*користење)\\b");
    private static final Pattern SALES=Pattern.compile(
        "(?iu)(?:клуб\\s*цена|редовна\\s*цена|cashback|достава|гаранција|"+
        "\\bрати\\b|\\bден(?:ари)?\\b|\\bмкд\\b|кошничка|кредит)");
    private static final Pattern CHUNK_SPLIT=Pattern.compile(
        "(?iu)\\s+[-–—|]\\s*(?=(?:моќност|снага|power|капацитет|дијагонала|"+
        "брзини|програми|гас|енергетска|димензи|тежина|грејач|температура|"+
        "репродукција|волумен|ресолуција|резолуција|проток|бучност)\\b)");

    static List<GoogleModelResearch.Fact> select(
            String model,String url,String technical,String shortDesc,
            String fullTitle,String category,Map<String,String> original) {
        List<GoogleModelResearch.Fact> result=new ArrayList<>();
        if(!SetecPageData.isOfficialProductUrl(url))return result;
        String identity=(nz(category)+" "+nz(fullTitle)+" "+nz(model)+" "+
            prefix(nz(technical),1200)+" "+prefix(nz(shortDesc),250))
            .toUpperCase(Locale.ROOT);
        Kind kind=classify(identity);
        Map<String,String> data=new LinkedHashMap<>(original);
        for(String part:new String[]{technical,shortDesc,fullTitle}){
            if(part==null||part.isEmpty())continue;
            String txt=CHUNK_SPLIT.matcher(part.replace('\u00a0',' ')
                .replace('•','\n').replace('·','\n').replace('\r','\n')
                .replace('\t','\n')).replaceAll("\n");
            int count=0;
            for(String raw:txt.split("\\n")){
                if(count++>110)break;
                String line=raw.trim().replaceAll("\\s+"," ")
                    .replaceFirst("^[-–—:;]+\\s*","");
                if(line.length()<3||line.length()>270)continue;
                if(STOP.matcher(line).find())break;
                if(SALES.matcher(line).find())continue;
                parseCommon(data,line);
                parseSpecific(kind,data,line);
            }
        }
        List<String> priority=priority(kind);
        for(String name:priority){
            if(result.size()>=5)break;
            String value=data.get(name);
            if(value!=null&&!value.isEmpty())result.add(new GoogleModelResearch.Fact(name,value,url));
        }
        if(result.size()<5 && kind==Kind.UNKNOWN){
            // For unknown categories, still surface only recognized, well-labelled
            // technical facts in original source order.
            for(Map.Entry<String,String> e:data.entrySet()){
                if(result.size()>=5)break;
                String name=e.getKey(),value=e.getValue();
                if(!priority.contains(name)&&value!=null&&!value.isEmpty())
                    result.add(new GoogleModelResearch.Fact(name,value,url));
            }
        }
        return result;
    }

    private static String nz(String s){return s==null?"":s;}
    private static String prefix(String s,int n){return s.substring(0,Math.min(n,s.length()));}
    private static boolean has(String text,String... terms){
        for(String t:terms)if(text.contains(t))return true;
        return false;
    }

    private static Kind classify(String t){
        // Prefer unmistakable appliance type keywords. Short technical headers
        // often contain the only category text, e.g. "TEFAL TT420D30\nТостер".
        if(has(t,"ТОСТЕР","TOСТЕР","TOCTEP","TOASTER","СЕНДВИЧАРА","СЕНДВИЧ ТОСТЕР","АПАРАТ ЗА ТОСТ"))return Kind.TOASTER;
        if(has(t,"ФЕН ЗА КОСА","ФЕН ","HAIR DRYER","ФЕН ЗА СУШЕЊЕ"))return Kind.HAIR_DRYER;
        if(has(t,"АСПИРАТОР","КУЈНСКИ АСПИРАТОР","COOKER HOOD","RANGE HOOD"))return Kind.HOOD;
        if(has(t,"КЛИМА","КЛИМАТИЗЕР","AIR CONDITIONER","ТОПЛИНСКА ПУМПА","ИНВЕРТЕР КЛИМА"))
            return Kind.AIR_CONDITIONER;
        if(has(t,"МАШИНА ЗА САДОВИ","МАШИНА ЗА МИЕЊЕ САДОВИ","DISHWASHER"))return Kind.DISHWASHER;
        if(has(t,"МАШИНА ЗА ПЕРЕЊЕ","ПЕРАЛНА","ПРАЛНА","WASHING MACHINE",
                " TWM ","MF110W"))return Kind.WASHER;
        if(has(t,"МАШИНА ЗА СУШЕЊЕ","СУШИЛКА ЗА АЛИШТА","TUMBLE DRYER"))return Kind.CLOTHES_DRYER;
        if(has(t,"ФРИЖИДЕР","ЛАДИЛНИК","ЗАМРЗНУВАЧ","FRIDGE","FREEZER","REFRIGERATOR"))
            return Kind.FRIDGE;
        if(has(t,"МИКРОБРАНОВА","МИКРОБРАНСКА","MICROWAVE"))return Kind.MICROWAVE;
        if(has(t,"ФРИТЕЗА","AIR FRYER","AIRFRYER","FRYER"))return Kind.AIR_FRYER;
        if(has(t,"РЕРНА","ДУХОВНА","OVEN","ПЕЧКА ЗА ВГРАДУВАЊЕ"))return Kind.OVEN;
        if(has(t,"ПЛОЧА ЗА ГОТВЕЊЕ","ИНДУКЦИСКА ПЛОЧА","COOKTOP"," HOB"))return Kind.HOB;
        if(has(t,"ПРАВОСМУКАЛКА","VACUUM CLEANER","РОБОТ ПРАВОСМУКАЛКА"))
            return Kind.VACUUM;
        if(has(t,"БЛЕНДЕР","BLENDER","NUTRIBULLET"))return Kind.BLENDER;
        if(has(t,"МИКСЕР","HAND MIXER","STAND MIXER"))return Kind.MIXER;
        if(has(t,"ЕЛЕКТРИЧЕН БОКАЛ","БОКАЛ ЗА ВОДА","KETTLE","БРЗОВАР","КУВАЛО"))
            return Kind.KETTLE;
        if(has(t,"КАФЕМАТ","КАФЕ МАШИНА","АПАРАТ ЗА КАФЕ","COFFEE MAKER","ESPRESSO"))
            return Kind.COFFEE;
        if(has(t,"ПЕГЛА","IRON","ПАРНА СТАНИЦА"))return Kind.IRON;
        if(has(t,"ПРЕСА ЗА КОСА","ФИГАРО","СТАЈЛЕР","HAIR STRAIGHTENER"))return Kind.HAIR_STYLER;
        if(has(t,"ВЕНТИЛАТОР","FAN ","AIR CIRCULATOR"))return Kind.FAN;
        if(has(t,"ГРЕАЛКА","ЕЛЕКТРИЧНО ГРЕЕЊЕ","КОНВЕКТОР","RADIATOR","HEATER"))
            return Kind.HEATER;
        if(has(t,"БОЈЛЕР","WATER HEATER"))return Kind.BOILER;
        if(has(t,"СЕЦКО","МУЛТИПРАКТИК","FOOD PROCESSOR","СОКОВНИК","JUICER"))
            return Kind.FOOD_PROCESSOR;
        if(has(t,"ПЕЧАТАЧ","PRINTER","MULTIFUNCTION PRINTER"))return Kind.PRINTER;
        if(has(t,"РУТЕР","ROUTER","WI-FI ROUTER"))return Kind.ROUTER;
        if(has(t,"ЗВУЧНИК","SPEAKER","SOUNDBAR","САУНДБАР"))return Kind.SPEAKER;
        if(has(t,"ЛАПТОП","NOTEBOOK","MACBOOK","ASPIRE","VIVOBOOK","ZENBOOK",
                "IDEAPAD","THINKPAD","LEGION","NITRO","INSPIRON"))return Kind.LAPTOP;
        if(has(t,"ТЕЛЕФОН","СМАРТФОН","IPHONE","GALAXY S","REDMI","POCO","ТАБЛЕТ"))
            return Kind.PHONE;
        if(has(t,"ТЕЛЕВИЗОР","ТЕЛЕВИЗОРИ","ТВ ","SMART TV"," QLED"," OLED",
                "MINI-LED","ULTRAHD","GOOGLE TV","ANDROID TV",
                "TELEVISION","LED TV"," 4K TV"," TV","МОНИТОР","MONITOR"))
            return Kind.TV;
        return Kind.UNKNOWN;
    }

    private static List<String> priority(Kind k){
        switch(k){
            case TOASTER:return Arrays.asList("Моќност","Број на парчиња","Степени на печење",
                "Плочи","Одмрзнување","Тацна за трошки","Автоматско исклучување","Термостат");
            case HAIR_DRYER:return Arrays.asList("Моќност","Брзини","Нивоа на топлина",
                "Јонизатор","Ладен воздух","Дифузер","Должина на кабел");
            case HOOD:return Arrays.asList("Проток на воздух","Брзини","Бучава",
                "Ширина","Филтер","Осветлување","Моќност");
            case AIR_CONDITIONER:return Arrays.asList("Капацитет BTU","Ладење","Греење",
                "Енергетска класа","SEER","SCOP","Wi-Fi","Работна температура",
                "Проток на воздух");
            case DISHWASHER:return Arrays.asList("Комплети садови","Програми",
                "Енергетска класа","Потрошувачка вода","Бучава","Ширина");
            case WASHER:return Arrays.asList("Капацитет на перење","Центрифуга",
                "Енергетска класа","Програми","Димензии");
            case CLOTHES_DRYER:return Arrays.asList("Капацитет на сушење","Енергетска класа",
                "Топлинска пумпа","Програми","Димензии");
            case FRIDGE:return Arrays.asList("Капацитет","Енергетска класа",
                "No Frost","Димензии","Бучава");
            case OVEN:return Arrays.asList("Капацитет","Енергетска класа",
                "Програми","Температура","Димензии","Моќност");
            case HOB:return Arrays.asList("Тип плоча","Број на рингли","Моќност",
                "Ширина","Димензии");
            case MICROWAVE:return Arrays.asList("Капацитет","Моќност",
                "Грил","Програми","Димензии");
            case VACUUM:return Arrays.asList("Моќност","Всисна моќ","Капацитет",
                "Батерија","Автономија","Филтер");
            case BLENDER:return Arrays.asList("Моќност","Капацитет","Брзини",
                "Број на вртежи","Пулс","Материјал");
            case MIXER:return Arrays.asList("Моќност","Брзини","Капацитет",
                "Пулс","Материјал");
            case KETTLE:return Arrays.asList("Капацитет","Моќност","Термостат",
                "Автоматско исклучување","Материјал");
            case COFFEE:return Arrays.asList("Притисок","Моќност","Капацитет",
                "Тип кафе","Мелница","Автоматско исклучување");
            case AIR_FRYER:return Arrays.asList("Капацитет","Моќност",
                "Температура","Програми","Тајмер");
            case IRON:return Arrays.asList("Моќност","Пареа","Капацитет",
                "Керамичка плоча","Автоматско исклучување");
            case HAIR_STYLER:return Arrays.asList("Температура","Моќност",
                "Нивоа на топлина","Керамичка плоча","Автоматско исклучување");
            case FAN:return Arrays.asList("Моќност","Брзини",
                "Дијаметар","Тајмер","Осцилација");
            case HEATER:return Arrays.asList("Моќност","Термостат","Површина",
                "Нивоа на топлина","Тајмер");
            case BOILER:return Arrays.asList("Капацитет","Моќност",
                "Температура","Димензии");
            case FOOD_PROCESSOR:return Arrays.asList("Моќност","Капацитет",
                "Брзини","Материјал","Пулс");
            case PRINTER:return Arrays.asList("Тип печатач","Брзина печатење",
                "Резолуција","Wi-Fi","Двострано печатење");
            case ROUTER:return Arrays.asList("Wi-Fi стандард","Брзина",
                "Портови","Фреквенција","Антени");
            case SPEAKER:return Arrays.asList("Моќност","Bluetooth",
                "Батерија","Време на работа","Поврзување");
            case LAPTOP:return Arrays.asList("Процесор","RAM","Диск","Графичка","Екран");
            case PHONE:return Arrays.asList("Екран","RAM","Диск","Батерија","Камера");
            case TV:return Arrays.asList("Дијагонала","Резолуција",
                "Освежување","Технологија на екран","Smart TV",
                "Оперативен систем","HDR","HDMI");
            default:return Arrays.asList("Моќност","Капацитет","Брзини",
                "Програми","Температура","Димензии","Бучава","Тежина",
                "Материјал","Wi-Fi","Тајмер");
        }
    }

    private static void parseCommon(Map<String,String> f,String line){
        // Stand-alone wattage is common in the Setec technical list ("950W").
        offer(f,"Моќност",line,"(?iu)(?:моќност|снага|power|power consumption|"+
            "електрична\\s*моќ)\\s*[:\\-]?\\s*(\\d{2,5}(?:[.,]\\d+)?\\s*(?:W|В|ват[аи]?))\\b");
        if(line.matches("(?iu)^\\d{2,5}\\s*(?:W|В|ват[аи]?)\\b.*"))
            offer(f,"Моќност",line,"(?iu)^(\\d{2,5}\\s*(?:W|В|ват[аи]?))\\b");
        // Whole quoted product title may include 2x40W lamps: do not classify as motor power.
        // Multi-lamp power (2x40W) is lighting, not motor power; the
        // labelled/standalone power match above intentionally ignores it.
        offer(f,"Моќност",line,"(?iu)(?:моќност|снага|power)\\s*[:\\-]?\\s*"+
            "(\\d{1,2}(?:[.,]\\d+)?\\s*kW)\\b");

        offer(f,"Капацитет",line,"(?iu)(?:капацитет(?:\\s*на\\s*садот)?|"+
            "волумен(?:\\s*на\\s*садот)?|capacity|запремнина|сад\\s*со\\s*капацитет)"+
            "\\s*[:\\-]?\\s*(\\d{1,4}(?:[.,]\\d+)?\\s*(?:л|L|лит(?:ри|ра)|ml|мл))\\b");
        offer(f,"Брзини",line,"(?iu)\\b(\\d{1,2})\\s*(?:степени\\s*на\\s*брзина|"+
            "брзини|speed\\s*settings|speeds)\\b");
        offer(f,"Брзини",line,"(?iu)(?:број\\s*на\\s*брзини|брзини)\\s*[:\\-]?\\s*(\\d{1,2})\\b");
        offer(f,"Програми",line,"(?iu)\\b(\\d{1,2})\\s*(?:програми|programs|programmes)\\b");
        offer(f,"Програми",line,"(?iu)(?:број\\s*на\\s*програми|програми)\\s*[:\\-]?\\s*(\\d{1,2})\\b");
        offer(f,"Температура",line,"(?iu)(?:температура|temperature)\\s*[:\\-]?\\s*"+
            "(\\d{2,3}\\s*(?:°\\s*C|°C|степени))");
        offer(f,"Димензии",line,"(?iu)(?:димензии|dimensions)\\s*(?:\\([^)]{0,30}\\))?"+
            "\\s*[:\\-]?\\s*(\\d{2,4}(?:[.,]\\d+)?\\s*[x×х]\\s*"+
            "\\d{2,4}(?:[.,]\\d+)?\\s*[x×х]\\s*"+
            "\\d{2,4}(?:[.,]\\d+)?\\s*(?:cm|mm|цм|см|мм)?)");
        offer(f,"Бучава",line,"(?iu)(?:бучава|бучност|бучавост|noise|звучна\\s*моќ)"+
            "[^\\n]{0,45}?(\\d{2,3}(?:[.,]\\d+)?\\s*(?:dB|дб))\\b");
        offer(f,"Тежина",line,"(?iu)(?:тежина|weight)\\s*[:\\-]?\\s*"+
            "(\\d{1,3}(?:[.,]\\d+)?\\s*(?:kg|кг))\\b");
        offer(f,"Ширина",line,"(?iu)(?:ширина|width|вграден\\s*аспиратор)"+
            "\\s*[:\\-]?\\s*(\\d{2,3}\\s*(?:cm|цм|см))\\b");

        if(yes(line,"автоматско\\s*(?:исклучување|гасење)|auto(?:matic)?\\s*shut\\s*off"))
            f.putIfAbsent("Автоматско исклучување","Да");
        if(yes(line,"\\b(?:wifi|wi-fi|вграден\\s*wifi)\\b"))f.putIfAbsent("Wi-Fi","Да");
        if(yes(line,"\\b(?:тајмер|timer)\\b"))f.putIfAbsent("Тајмер","Да");
        if(yes(line,"\\b(?:термостат|thermostat)\\b"))f.putIfAbsent("Термостат","Да");
        if(yes(line,"\\b(?:no\\s*frost)\\b"))f.putIfAbsent("No Frost","Да");
    }

    private static void parseSpecific(Kind kind,Map<String,String> f,String line){
        switch(kind){
        case TV:
            parseTv(f,line);
            break;
        case TOASTER:
            offer(f,"Број на парчиња",line,
                "(?iu)\\b(\\d{1,2})\\s*(?:парчиња\\s*(?:тост|лепчиња)?|"+
                "места\\s*за\\s*лепчиња|слотови|slices)\\b");
            offer(f,"Степени на печење",line,
                "(?iu)\\b(\\d{1,2})\\s*(?:степени\\s*на\\s*(?:работа|печење|тостирање)|"+
                "поставки\\s*на\\s*јачина|прилагодувања\\s*на\\s*тостот|"+
                "нивоа\\s*на\\s*печење)\\b");
            offer(f,"Степени на печење",line,
                "(?iu)(?:регулација\\s*на\\s*печењето|степени\\s*на\\s*печење)"+
                "\\s*[:\\-]?\\s*(\\d{1,2})");
            if(yes(line,"(?:нелеплив(?:и|а|о)|non[- ]stick)\\s*плоч[аи]"))
                f.putIfAbsent("Плочи","Нелепливи");
            if(yes(line,"(?:одмрзнување|defrost)"))
                f.putIfAbsent("Одмрзнување","Да");
            if(yes(line,"(?:тавичка|тацна|послужавник|сад)\\s*за\\s*трошки|"+
                "crumb\\s*tray|отстранлив\\s*сад\\s*за\\s*трошки"))
                f.putIfAbsent("Тацна за трошки","Да");
            break;
        case HAIR_DRYER:
        case HAIR_STYLER:
            offer(f,"Нивоа на топлина",line,"(?iu)\\b(\\d{1,2})\\s*"+
                "(?:нивоа\\s*на\\s*топлина|температурни\\s*нивоа|"+
                "температури|heat\\s*settings)\\b");
            if(yes(line,"\\bјонизатор\\b|\\bioniz(?:er|ator)\\b|ionic\\s*function"))
                f.putIfAbsent("Јонизатор","Да");
            if(yes(line,"ладен\\s*воздух|cool\\s*shot|cold\\s*air"))
                f.putIfAbsent("Ладен воздух","Да");
            if(yes(line,"\\bдифузер\\b|\\bdiffuser\\b"))
                f.putIfAbsent("Дифузер","Да");
            offer(f,"Должина на кабел",line,"(?iu)(?:кабел|cable)\\s*"+
                "(\\d(?:[.,]\\d{1,2})?\\s*(?:m|м))\\b");
            offer(f,"Должина на кабел",line,"(?iu)\\b"+
                "(\\d(?:[.,]\\d{1,2})?\\s*(?:m|м))\\s*(?:кабел|cable)\\b");
            if(yes(line,"керамички\\s*плочи|ceramic\\s*plate"))
                f.putIfAbsent("Керамичка плоча","Да");
            break;
        case HOOD:
            offer(f,"Проток на воздух",line,"(?iu)(?:капацитет\\s*"+
                "(?:на\\s*)?(?:одвод|проток|аспирација|извлекување)?|проток\\s*на\\s*воздух|"+
                "извлекување|екстракција)\\s*[:\\-]?\\s*"+
                "(\\d{2,4}(?:[.,]\\d+)?\\s*(?:m\\s*3\\s*/\\s*h|m³/h|м3/ч|"+
                "м³/ч|cb3|м\\s*кубни))\\b");
            offer(f,"Проток на воздух",line,
                "(?iu)\\b(\\d{2,4}\\s*(?:m3/h|m³/h|м3/ч|м³/ч))\\b");
            if(yes(line,"алуминиумски\\s*филтер|aluminium\\s*filter|"+
                "метален\\s*филтер|metal\\s*filter"))
                f.putIfAbsent("Филтер","Метален");
            if(yes(line,"(?:лед|LED)\\s*осветлување"))
                f.putIfAbsent("Осветлување","LED");
            break;
        case AIR_CONDITIONER:
            offer(f,"Капацитет BTU",line,
                "(?iu)(?:капацитет\\s*)?(\\d{4,5}\\s*BTU)\\b");
            offer(f,"Ладење",line,
                "(?iu)(?:капацитет\\s*(?:на\\s*)?)?ладење\\s*[:\\-]?\\s*"+
                "(\\d{1,2}(?:[.,]\\d{1,3})?\\s*kW)\\b");
            offer(f,"Греење",line,
                "(?iu)(?:капацитет\\s*(?:на\\s*)?)?греење\\s*[:\\-]?\\s*"+
                "(\\d{1,2}(?:[.,]\\d{1,3})?\\s*kW)\\b");
            offer(f,"SEER",line,"(?iu)\\bSEER\\b(?:\\s*\\([^)]{0,25}\\)|"+
                "\\s*за\\s*ладење)?\\s*[:\\-]?\\s*(\\d{1,2}(?:[.,]\\d{1,2})?)\\b");
            offer(f,"SCOP",line,"(?iu)\\bSCOP\\b(?:\\s*\\([^)]{0,25}\\)|"+
                "\\s*за\\s*греење)?\\s*[:\\-]?\\s*(\\d{1,2}(?:[.,]\\d{1,2})?)\\b");
            offer(f,"Работна температура",line,
                "(?iu)(?:греење\\s*до|работна\\s*температура)\\s*"+
                "(-\\s*\\d{1,2}\\s*(?:°C|°\\s*C|степени))");
            offer(f,"Проток на воздух",line,
                "(?iu)(?:проток\\s*на\\s*воздух|циркулација\\s*на\\s*воздух)"+
                "\\s*[:\\-]?\\s*(\\d{2,4}\\s*(?:m3/h|m³/h|м3/ч))");
            if(yes(line,"(?iu)\\b(?:инвертер|inverter)\\b"))f.putIfAbsent("Инвертер","Да");
            offer(f,"Енергетска класа",line,
                "(?iu)\\b([A-GА-Г]\\+{0,3})\\s+(?:на\\s*ладење|енергетска\\s*класа)");
            break;
        case DISHWASHER:
            offer(f,"Комплети садови",line,
                "(?iu)(?:капацитет\\s*[:\\-]?\\s*)?"+
                "(\\d{1,2})\\s*(?:сетови|комплети|места\\s*за\\s*садови|place\\s*settings)\\b");
            offer(f,"Потрошувачка вода",line,
                "(?iu)(?:потрошувачка\\s*на\\s*вода|water\\s*consumption)\\s*[:\\-]?\\s*"+
                "(\\d{1,2}(?:[.,]\\d)?\\s*(?:l|L|л|литри))\\b");
            break;
        case CLOTHES_DRYER:
            offer(f,"Капацитет на сушење",line,
                "(?iu)(?:капацитет(?:\\s*на\\s*сушење)?|полнење)\\s*[:\\-]?\\s*"+
                "(\\d{1,2}(?:[.,]\\d)?\\s*(?:kg|кг))\\b");
            if(yes(line,"топлинска\\s*пумпа|heat\\s*pump"))
                f.putIfAbsent("Топлинска пумпа","Да");
            break;
        case VACUUM:
            offer(f,"Всисна моќ",line,
                "(?iu)(?:всисна\\s*моќ|suction\\s*power|усисна\\s*моќ)"+
                "\\s*[:\\-]?\\s*(\\d{1,4}\\s*(?:AW|W|Pa))\\b");
            offer(f,"Автономија",line,
                "(?iu)(?:автономија|време\\s*на\\s*работа|runtime)\\s*[:\\-]?\\s*"+
                "(\\d{1,3}\\s*(?:мин(?:ути)?|min|час(?:а)?|h))\\b");
            if(yes(line,"\\bHEPA\\s*филтер\\b|\\bHEPA\\b"))
                f.putIfAbsent("Филтер","HEPA");
            break;
        case BLENDER:
        case MIXER:
        case FOOD_PROCESSOR:
            offer(f,"Број на вртежи",line,
                "(?iu)\\b(\\d{4,6}\\s*(?:RPM|вртежи))\\b");
            if(yes(line,"\\bпулс\\b|\\bpulse\\b"))f.putIfAbsent("Пулс","Да");
            if(yes(line,"стаклен\\s*сад|glass\\s*jar"))f.putIfAbsent("Материјал","Стаклен сад");
            break;
        case KETTLE:
            if(yes(line,"не[р]?ѓосувачки\\s*челик|stainless\\s*steel"))
                f.putIfAbsent("Материјал","Нерѓосувачки челик");
            break;
        case COFFEE:
            offer(f,"Притисок",line,"(?iu)(?:притисок|pressure|пумпа)\\s*[:\\-]?\\s*"+
                "(\\d{1,2}\\s*bar)\\b");
            offer(f,"Тип кафе",line,
                "(?iu)(?:тип\\s*кафе|кафе)\\s*[:\\-]?\\s*(капсули|мелено|зрна)");
            if(yes(line,"мелница|grinder"))f.putIfAbsent("Мелница","Да");
            break;
        case AIR_FRYER:
            offer(f,"Температура",line,
                "(?iu)(?:температура|temperature)\\s*[:\\-]?\\s*"+
                "(\\d{2,3}\\s*[-–]\\s*\\d{2,3}\\s*°?C)");
            break;
        case IRON:
            offer(f,"Пареа",line,"(?iu)(?:континуирана\\s*пареа|пареа|steam)"+
                "\\s*[:\\-]?\\s*(\\d{2,4}\\s*(?:g/min|гр/мин))\\b");
            if(yes(line,"керамичка\\s*плоча|ceramic\\s*soleplate"))
                f.putIfAbsent("Керамичка плоча","Да");
            break;
        case FAN:
            offer(f,"Дијаметар",line,
                "(?iu)(?:дијаметар|diameter)\\s*[:\\-]?\\s*(\\d{2,3}\\s*cm)");
            if(yes(line,"осцилација|oscillation"))f.putIfAbsent("Осцилација","Да");
            break;
        case HEATER:
            offer(f,"Површина",line,
                "(?iu)(?:површина|простори(?:и|ја)\\s*до|room\\s*size)\\s*[:\\-]?\\s*"+
                "(\\d{1,3}\\s*(?:м2|m2|m²|м²))\\b");
            break;
        case MICROWAVE:
            if(yes(line,"\\bгрил\\b|\\bgrill\\b"))f.putIfAbsent("Грил","Да");
            break;
        case HOB:
            offer(f,"Број на рингли",line,
                "(?iu)\\b(\\d{1,2})\\s*(?:рингли|грејни\\s*зони|zones)\\b");
            if(yes(line,"индукци(?:ска|ја)|induction"))f.putIfAbsent("Тип плоча","Индукциска");
            break;
        case PRINTER:
            offer(f,"Брзина печатење",line,
                "(?iu)(?:брзина\\s*на\\s*печатење|print\\s*speed)\\s*[:\\-]?\\s*"+
                "(\\d{1,3}\\s*(?:ppm|стр/мин))\\b");
            if(yes(line,"двострано\\s*печатење|duplex"))
                f.putIfAbsent("Двострано печатење","Да");
            break;
        case ROUTER:
            offer(f,"Wi-Fi стандард",line,
                "(?iu)\\b(Wi-?Fi\\s*[4567]|802\\.11[a-z]{1,3})\\b");
            offer(f,"Портови",line,
                "(?iu)(\\d{1,2}\\s*(?:LAN|Ethernet|RJ45)\\s*порт(?:ови)?)");
            break;
        case SPEAKER:
            if(yes(line,"\\bbluetooth\\b"))f.putIfAbsent("Bluetooth","Да");
            offer(f,"Време на работа",line,
                "(?iu)(?:време\\s*на\\s*работа|playback\\s*time|автономија)\\s*"+
                "(\\d{1,3}\\s*(?:h|час(?:а)?))\\b");
            break;
        default:break;
        }
    }

    /**
     * Setec TV listings often use English freeform bullets, not label/value:
     *  55" (138cm) UltraHD QLED Smart TV
     *  Resolution: 3.840 x 2.160 (4K Ultra HD)
     *  144Hz (VRR), VIDAA U7, OS: TITAN, HDMI x3.
     * Only claim refresh frequency when a genuine Hz measurement is explicit.
     */
    private static void parseTv(Map<String,String> f,String line){
        // Earlier general parser sometimes captures a value like 3840x2160.
        // Normalize that value before applying the TV-specific selector.
        String rawResolution=f.get("Резолуција");
        if(rawResolution!=null){
            String clean=rawResolution.replaceAll("(?<=\\d)[.,](?=\\d{3}\\b)","")
                .replaceAll("\\s*[x×]\\s*","×");
            if(clean.matches("(?:1280×720|1366×768|1920×1080|2560×1440|"+
                    "3840×2160|4096×2160|7680×4320)"))
                f.put("Резолуција",clean);
        }
        // Limit standalone inch values to plausible display diagonals.
        offer(f,"Дијагонала",line,
            "(?iu)(?:дијагонала|screen\\s*size|големина\\s*на\\s*екран)\\s*[:\\-]?\\s*"+
            "(\\d{2,3}(?:[.,]\\d)?\\s*(?:\"|”|″|inch|инч(?:и)?))");
        offer(f,"Дијагонала",line,
            "(?iu)(?<![\\p{Alnum}])(\\d{2,3}(?:[.,]\\d)?\\s*(?:\"|”|″))(?=\\s|$|\\()");
        // English "Resolution 4K 3840x2160" and dotted thousands (3.840 x 2.160).
        // A vague "4K UHD" title must not block a later exact 3840x2160 value.
        if(!f.containsKey("Резолуција")||
           !f.get("Резолуција").contains("×")){
            Matcher resolution=Pattern.compile(
                "(?iu)(?:resolution|резолуција|ultrahd\\s+resolution)"+
                "[^;\\n]{0,34}?"+
                "((?:[1-9][.,]\\d{3}|\\d{3,4})\\s*[x×]\\s*(?:[1-9][.,]\\d{3}|\\d{3,4}))")
                .matcher(line);
            if(!resolution.find()){
                resolution=Pattern.compile(
                    "(?iu)(?<!\\d)((?:[1-9][.,]\\d{3}|\\d{3,4})\\s*[x×]\\s*"+
                    "(?:[1-9][.,]\\d{3}|\\d{3,4}))(?!\\d)").matcher(line);
                if(!resolution.find())resolution=null;
            }
            if(resolution!=null){
                String value=resolution.group(1).replace(".","")
                    .replace(",","").replaceAll("\\s*[x×]\\s*","×");
                if(value.matches("(?:1280×720|1366×768|1920×1080|2560×1440|"+
                        "3840×2160|4096×2160|7680×4320)"))
                    f.put("Резолуција",value);
            }
        }
        if(!f.containsKey("Резолуција")){
            if(Pattern.compile("(?iu)\\b(?:4K\\s*(?:ULTRA\\s*HD|UHD)|ULTRA\\s*HD\\s*4K)\\b")
                    .matcher(line).find())f.put("Резолуција","4K UHD");
            else if(Pattern.compile("(?iu)\\b(?:FULL\\s*HD|FHD)\\b")
                    .matcher(line).find())f.put("Резолуција","Full HD");
            else if(Pattern.compile("(?iu)\\b(?:HD\\s*READY)\\b")
                    .matcher(line).find())f.put("Резолуција","HD Ready");
        }
        // Highest precedence to true native/VRR tagged frequency. Avoid
        // interpreting "Game Accelerator 120Hz" as native panel refresh.
        offer(f,"Освежување",line,
            "(?iu)(?:refresh\\s*rate|освежување(?:\\s*на\\s*екран)?|"+
            "panel\\s*frequency|фреквенција\\s*на\\s*екран)\\s*[:\\-]?\\s*"+
            "(\\d{2,3}\\s*Hz)\\b");
        offer(f,"Освежување",line,
            "(?iu)^\\s*(\\d{2,3}\\s*Hz)(?=\\s*\\(\\s*VRR\\s*\\)|\\s*$)");
        // 'Mini-LED' contains LED, 'QLED' contains LED. Rank more specific first.
        if(!f.containsKey("Технологија на екран")){
            Matcher tech=Pattern.compile(
                "(?iu)(?<![A-Z])(?:MINI[\\s-]*LED|NEO\\s*QLED|QLED|OLED|"+
                "QNED|NANO\\s*CELL|LED|LCD)(?![A-Z])").matcher(line);
            if(tech.find()){
                String raw=tech.group().toUpperCase(Locale.ROOT).replace(" ","");
                if(raw.contains("MINI"))f.put("Технологија на екран","Mini-LED");
                else if(raw.contains("QLED"))f.put("Технологија на екран","QLED");
                else if(raw.contains("OLED"))f.put("Технологија на екран","OLED");
                else if(raw.contains("QNED"))f.put("Технологија на екран","QNED");
                else if(raw.contains("NANO"))f.put("Технологија на екран","NanoCell");
                else if(raw.equals("LED")||raw.equals("LCD"))
                    f.put("Технологија на екран",raw);
            }
        }
        // Smart and OS are intentionally separate facts: smart does not mean Android.
        if(yes(line,"(?iu)\\b(?:SMART\\s*(?:TV)?|GOOGLE\\s*TV|ANDROID\\s*TV|"+
                "VIDAA|TIZEN|WEBOS|TITAN\\s*OS|SAPHI)\\b"))
            f.putIfAbsent("Smart TV","Да");
        offer(f,"Оперативен систем",line,
            "(?iu)\\bOS\\s*[:\\-]\\s*(TITAN|VIDAA|TIZEN|WEBOS|ANDROID\\s*TV|GOOGLE\\s*TV|SAPHI)\\b");
        if(Pattern.compile("(?iu)\\bGOOGLE\\s*TV\\b").matcher(line).find())
            f.putIfAbsent("Оперативен систем","Google TV");
        if(Pattern.compile("(?iu)\\bVIDAA(?:\\s*(?:OS|U\\d+))?\\b").matcher(line).find())
            f.putIfAbsent("Оперативен систем","VIDAA");
        if(Pattern.compile("(?iu)\\b(?:TIZEN|WEBOS|TITAN\\s*OS)\\b").matcher(line).find()){
            if(!f.containsKey("Оперативен систем"))
                f.put("Оперативен систем",line.trim().length()<25?line.trim():"Tizen/webOS/Titan");
        }
        if(Pattern.compile("(?iu)\\bHDR\\s*10\\s*\\+?\\b").matcher(line).find())
            f.putIfAbsent("HDR",line.replaceAll("(?iu)^.*?(HDR\\s*10\\s*\\+?).*$","$1").trim());
        if(Pattern.compile("(?iu)\\bDOLBY\\s*VISION\\b").matcher(line).find())
            f.putIfAbsent("HDR","Dolby Vision");
        offer(f,"HDMI",line,"(?iu)(?:connections?\\s*[:\\-]?\\s*)?(\\d)\\s*x\\s*HDMI\\b");
        offer(f,"HDMI",line,"(?iu)\\bHDMI\\s*x\\s*(\\d)\\b");
        if(f.containsKey("HDMI")&&f.get("HDMI").matches("\\d"))
            f.put("HDMI",f.get("HDMI")+" порти");
    }

    private static boolean yes(String text,String regex){
        if(Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE)
                .matcher(text).find()){
            return !Pattern.compile("(?iu)(?:без|нема|no|without|not)\\s+.{0,16}(?:"+regex+")")
                .matcher(text).find();
        }
        return false;
    }

    private static void offer(Map<String,String> facts,String label,String line,String regex){
        if(facts.containsKey(label))return;
        Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE)
            .matcher(line);
        if(!m.find()||m.groupCount()<1)return;
        String v=m.group(1);
        if(v==null)return;
        v=v.trim().replaceAll("[,;: .\\-–—|]+$","").trim();
        if(v.isEmpty()||v.length()>90||SALES.matcher(v).find())return;
        facts.put(label,v);
    }
}
