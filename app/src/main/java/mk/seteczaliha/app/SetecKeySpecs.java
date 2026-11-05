package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative Setec technical-specifications reader. The input MUST be text
 * from the exact, SKU-verified Setec product page, specifically its technical
 * specifications tab. Marketing, recommendations, delivery, finance and pricing
 * are excluded. It never estimates values absent from the original text.
 */
final class SetecKeySpecs {
    private SetecKeySpecs() {}

    private static final Pattern NOISE=Pattern.compile(
        "(?iu)(?:^|\\b)(?:клуб\\s*цена|редовна\\s*цена|шифра|гаранција|достава|" +
        "рати|рата|cashback|поени|ден(?:ари)?|достапност|кориснички|" +
        "правила|препорачани|поврзани\\s*производи|cookies|поставки|" +
        "кошничка|newsletter|промоција|телефон\\s*за\\s*контакт)(?:\\b|:)");
    private static final Pattern PRICE=Pattern.compile("(?iu)\\b\\d+[.,]\\d{3}\\s*(?:ден|мкд|mkd)\\b");
    private static final Pattern END=Pattern.compile(
        "(?iu)^(?:Оцени|Рецензии|Слични\\s*производи|Поврзани\\s*производи|" +
        "Препорачани|Правила|Достава|Гаранција|Купете|Опис\\s*на\\s*компанијата)\\b");

    static List<GoogleModelResearch.Fact> extract(String model,String sourceUrl,String specText) {
        List<GoogleModelResearch.Fact> out=new ArrayList<>();
        if(!SetecPageData.isOfficialProductUrl(sourceUrl)||specText==null||specText.trim().isEmpty())
            return out;
        String name=String.valueOf(model).toUpperCase(Locale.ROOT);
        boolean laptop=name.matches(".*(?:ЛАПТОП|NOTEBOOK|MACBOOK|ASPIRE|VIVOBOOK|ZENBOOK|IDEAPAD|THINKPAD|LATITUDE|PAVILION|PROBOOK|ELITEBOOK|NITRO|TUF|ROG|LEGION).*");
        boolean washer=name.matches(".*(?:ПЕРЕЊЕ|ПЕРАЛНА|WASH|WNH|TWM|MF110W|WF\\d|WW\\d).*");
        Map<String,String> found=new LinkedHashMap<>();

        String cleaned=specText.replace('\u00a0',' ').replaceAll("(?iu)<[^>]+>"," ")
            .replace('•','\n').replace('·','\n');
        String[] lines=cleaned.split("[\\r\\n]+");
        int inspected=0;
        for(String raw:lines){
            if(inspected++>=90)break;
            String line=raw.replaceAll("\\s+"," ").trim();
            if(line.isEmpty())continue;
            if(END.matcher(line).find())break;
            if(NOISE.matcher(line).find()||PRICE.matcher(line).find())continue;
            if(line.length()>190)continue; // Ignore long page navigation, legal text.
            String up=line.toUpperCase(Locale.ROOT);
            if(up.equals("ТЕХНИЧКИ СПЕЦИФИКАЦИИ")||up.equals("TECHNICAL SPECIFICATIONS"))continue;

            if(laptop){
                offer(found,"Процесор",line, "^(?:[-–]\\s*)?(?:процесор|cpu|processor)\\s*[:\\-]?\\s*(.{5,100})$", "cpu");
                offer(found,"Графичка",line, "^(?:графичка(?:\\s*карта)?|graphics|gpu|video)\\s*[:\\-]?\\s*(.{4,90})$", "gpu");
                offer(found,"RAM",line, "^(?:рам(?:\\s*меморија)?|ram(?:\\s*memory)?|memory)\\s*[:\\-]?\\s*(\\d{1,3}\\s*(?:GB|ГБ)(?:\\s+(?:DDR[345]|LPDDR[45](?:X)?)(?:\\s*\\d{3,5}(?:\\s*MHz)?)?)?).*$", "ram");
                offer(found,"Диск",line, "^(?:диск|ssd|storage|диск\\s*меморија)\\s*[:\\-]?\\s*((?:\\d{2,4}\\s*(?:GB|ГБ|TB|ТБ))\\s*(?:PCIe|NVMe|SATA|M\\.2|SSD|HDD|[\\w -]){0,30}).*$", "disk");
                offer(found,"Екран",line, "^(?:екран|дисплеј|screen|display)\\s*[:\\-]?\\s*(.{4,85})$", "screen");
                // Screen lines are occasionally in compact list form:
                // 15.6" FullHD (1920 x 1080) resolution.
                if(!found.containsKey("Екран")&&up.matches(".*\\b\\d{1,2}(?:[.,]\\d)?\\s*[\u0022”″].*(?:FHD|FULL\\s*HD|IPS|RESOLUTION|1920|2560).*"))
                    found.put("Екран",line);
            }
            if(washer){
                offer(found,"Капацитет на перење",line,
                    "^(?:капацитет(?:\\s*на\\s*перење)?|полнење|washing\\s*capacity)\\s*[:\\-]?\\s*(\\d{1,2}(?:[.,]\\d+)?\\s*(?:kg|кг)).*$","kg");
                offer(found,"Центрифуга",line,
                    "^(?:центрифуга|брзина(?:\\s*на\\s*центрифуга)?|spin\\s*speed)\\s*[:\\-]?\\s*(\\d{3,4}\\s*(?:rpm|врт(?:ежи)?(?:/мин)?|обр(?:/мин)?)).*$","rpm");
                if(!found.containsKey("Капацитет на перење")&&up.matches("^\\d{1,2}\\s*(?:KG|КГ)(?:\\b|$).*"))
                    found.put("Капацитет на перење",firstMatch(line,"\\b\\d{1,2}\\s*(?:kg|кг)\\b")+"");
                if(!found.containsKey("Центрифуга")&&up.matches("^\\d{3,4}\\s*(?:RPM|ВРТЕЖИ|ВРТ/МИН).*"))
                    found.put("Центрифуга",firstMatch(line,"\\d{3,4}\\s*(?:rpm|вртежи|врт/мин)"));
                offer(found,"Програми",line,"^(?:број\\s*на\\s*програми|програми|programmes|programs)\\s*[:\\-]?\\s*(\\d{1,2})\\b.*","programs");
                if(!found.containsKey("Програми")&&up.matches("^\\d{1,2}\\s*ПРОГРАМИ\\b.*"))
                    found.put("Програми",firstMatch(line,"\\d{1,2}"));
            }
            if(!found.containsKey("Енергетска класа")){
                Matcher energy=Pattern.compile(
                    "(?iu)^(?:енергетска(?:\\s*класа|\\s*ефикасност)?|energy\\s*(?:class|rating))\\s*[:\\-]?\\s*([A-GА-Г]\\+{0,3})\\b|^([A-GА-Г]\\+{0,3})\\s+(?:енергетска\\s*класа|energy\\s*class)\\b")
                    .matcher(line);
                if(energy.find()){
                    String value=energy.group(1)==null?energy.group(2):energy.group(1);
                    if(value!=null)found.put("Енергетска класа",value.toUpperCase(Locale.ROOT).replace('А','A').replace('В','B').replace('С','C'));
                }
            }
            offer(found,"Димензии",line,"^(?:димензии|dimensions)\\s*[:\\-]?\\s*(\\d{2,4}(?:[.,]\\d+)?\\s*[x×]\\s*\\d{2,4}(?:[.,]\\d+)?\\s*[x×]\\s*\\d{2,4}(?:[.,]\\d+)?\\s*(?:mm|cm|мм|см)?).*$","dimensions");
        }
        List<String> order=laptop?
            Arrays.asList("Процесор","RAM","Диск","Графичка","Екран","Енергетска класа","Димензии"):
            washer?Arrays.asList("Капацитет на перење","Центрифуга","Енергетска класа","Програми","Димензии"):
                Arrays.asList("Енергетска класа","Димензии","RAM","Диск","Екран","Процесор");
        for(String label:order){
            String value=found.get(label);
            if(value!=null&&!value.trim().isEmpty()&&out.size()<5)
                out.add(new GoogleModelResearch.Fact(label,value.trim(),sourceUrl));
        }
        return out;
    }

    private static void offer(Map<String,String> result,String key,String line,String regex,String type){
        if(result.containsKey(key))return;
        Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(line);
        if(!m.find())return;
        String value=m.group(1).trim().replaceAll("[,;.:\\s]+$","");
        if(value.isEmpty()||value.length()>95)return;
        if(type.equals("ram")&&!value.matches("(?iu)^\\d{1,3}\\s*(?:GB|ГБ).*"))return;
        if(type.equals("disk")&&!value.matches("(?iu)^\\d{2,4}\\s*(?:GB|ГБ|TB|ТБ).*"))return;
        if(type.equals("cpu")&&value.matches("(?iu).*(?:клуб|цена|денари|гаранција).*"))return;
        result.put(key,value);
    }

    private static String firstMatch(String text,String pattern){
        Matcher m=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text);
        return m.find()?m.group():"";
    }
}
