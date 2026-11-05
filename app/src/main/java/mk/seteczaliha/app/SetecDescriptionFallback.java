package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads already-extracted text from ONE verified Setec product page.
 *
 * Important: this is a deterministic text selector, not AI. It never infers
 * specifications that the page did not state. It provides the raw, source-
 * backed fallback for ALL catalog categories, including new categories.
 */
final class SetecDescriptionFallback {
    private SetecDescriptionFallback(){}

    private static final Pattern SALES=Pattern.compile(
        "(?iu)(?:ден(?:ари)?\\b|\\bмкд\\b|\\bmkd\\b|клуб\\s*цена|редовна\\s*цена|"+
        "cashback|поени|рати|кредит|кошничка|достава|гаранција|"+
        "претплат|newsletter|телефон\\s*за\\s*контакт|плаќање|нарачај|"+
        "сервисни\\s*центри|попуст|заштита\\s*на\\s*лични\\s*податоци|"+
        "политика\\s*за|права\\s*задржани|компанија|сите\\s*цени|данок|"+
        "сетек\\s*с.\\s*од\\s*техника|содржина\\s*на\\s*кошничка)");
    private static final Pattern STOP=Pattern.compile(
        "(?iu)^(?:технички\\s*спецификации|technical\\s*specifications|"+
        "слични\\s*производи|поврзани\\s*производи|препорачани|"+
        "рецензии|оцен[кии]|достава|контакт|претплатете|клуб\\s*цена|"+
        "редовна\\s*цена|шифра|гаранција|спецификации|купи|"+
        "во\\s*кошничка|image:|button:|дома\\s*/).*$");
    private static final Pattern TECH=Pattern.compile(
        "(?iu)(?:\\d(?:[.,]\\d+)?\\s*(?:W|kW|BTU|Hz|kHz|MHz|GHz|"+
        "GB|TB|ГБ|ТБ|kg|кг|g|гр|l|L|л|мл|ml|rpm|вртежи|"+
        "dB|дб|mAh|Wh|V|volt|cm|см|mm|мм|m²|m3/h|m³/h|"+
        "°C|inch|инч(?:и)?|MP)\\b|\\d{2,3}\\s*(?:\"|”|″)|"+
        "\\b(?:4K|8K|QLED|OLED|LCD|FHD|UHD|HDR10|HEPA|"+
        "SSD|HDD|RAM|LPDDR|DDR[345]|Wi-Fi|Bluetooth|HDMI|USB|"+
        "NVMe|PCIe|No\\s*Frost|Inverter|Инвертер|Google\\s*TV|"+
        "Android\\s*TV)\\b|\\d+\\s*[x×]\\s*\\d+)");
    private static final Pattern FEATURE=Pattern.compile(
        "(?iu)(?:моќност|капацитет|брзини|програми|температура|"+
        "резолуција|освежување|екран|дисплеј|димензи|процесор|"+
        "батерија|графичка|енергетска\\s*класа|сушење|"+
        "центрифуга|пареа|одмрзнување|тостирање|филтер|"+
        "регулација|јонизатор|дифузер|автоматско|проток|"+
        "светло|осветлување|печење|одложен|ладен\\s*воздух|"+
        "бучава|потрошувачка|притисок|поврзување|"+
        "operating\\s*system|resolution|power|capacity|speed|"+
        "frequency|refresh\\s*rate|energy\\s*class|no\\s*frost)");
    private static final Pattern LABELED=Pattern.compile(
        "(?iu)^([\\p{L}][\\p{L}0-9 /()_-]{2,42}?)\\s*[:=]\\s*(.{2,100})$");

    private static final class Item {
        final int index,quality;
        final String text;
        Item(int index,int quality,String text){
            this.index=index;this.quality=quality;this.text=text;
        }
    }

    static List<String> select(String model,String technical,String shortDesc,
                               String fullTitle,int limit) {
        List<Item> candidates=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        String pureModel=normalize(model).toLowerCase(Locale.ROOT);
        String pureTitle=normalize(fullTitle).toLowerCase(Locale.ROOT);
        String[] inputs={technical,shortDesc,fullTitle};
        int serial=0;
        for(int source=0;source<inputs.length;source++){
            if(inputs[source]==null||inputs[source].isEmpty())continue;
            String text=inputs[source].replace('\u00a0',' ')
                .replace('•','\n').replace('\u2022','\n').replace('·','\n')
                .replace('\r','\n').replace('\t','\n');
            // Preserve Setec's bullet layout; products occasionally use pipes.
            text=text.replaceAll("\\s+\\|\\s+","\\n");
            int count=0;
            for(String raw:text.split("\\n")){
                if(count++>=135)break;
                String line=normalize(raw).replaceFirst("^[-–—*;:]+\\s*","")
                    .replaceFirst("^(?:Краток\\s*опис|Опис)\\s*:\\s*","")
                    .replaceFirst("^(?:\\d+[.)]\\s+)","");
                if(line.length()<4||line.length()>230)continue;
                if(STOP.matcher(line).matches()||SALES.matcher(line).find())continue;
                String key=line.toLowerCase(Locale.ROOT);
                if(key.equals(pureModel)||key.equals(pureTitle))continue;
                if(!seen.add(key))continue;
                boolean isTech=TECH.matcher(line).find();
                boolean meaningful=FEATURE.matcher(line).find();
                boolean labeled=LABELED.matcher(line).matches();
                int quality=(isTech?8:0)+(meaningful?4:0)+(labeled?2:0)
                    +(source==0?3:source==1?1:0);
                if(!isTech&&!meaningful&&!labeled && source==2)continue;
                if(line.length()>135)quality-=5;
                if(line.length()>180)quality-=5;
                if(quality<2)continue;
                candidates.add(new Item(serial++,quality,line));
            }
        }
        candidates.sort(Comparator.comparingInt((Item i)->i.quality).reversed()
            .thenComparingInt(i->i.index));
        List<Item> selected=new ArrayList<>();
        for(Item item:candidates){
            if(selected.size()>=Math.max(0,Math.min(5,limit)))break;
            selected.add(item);
        }
        selected.sort(Comparator.comparingInt(i->i.index));
        List<String> result=new ArrayList<>();
        for(Item item:selected)result.add(item.text);
        return result;
    }

    static String summary(String model,String technical,String shortDesc,
                          String fullTitle) {
        List<String> lines=select(model,technical,shortDesc,fullTitle,5);
        StringBuilder out=new StringBuilder();
        for(String line:lines){
            if(out.length()>0)out.append('\n');
            out.append("• ").append(line);
        }
        return out.toString();
    }

    static List<GoogleModelResearch.Fact> labeledFacts(String model,String url,
            String technical,String shortDesc,String fullTitle) {
        List<GoogleModelResearch.Fact> facts=new ArrayList<>();
        if(!SetecPageData.isOfficialProductUrl(url))return facts;
        for(String line:select(model,technical,shortDesc,fullTitle,5)){
            Matcher m=LABELED.matcher(line);
            if(!m.matches())continue;
            String label=normalize(m.group(1)),value=normalize(m.group(2));
            if(value.length()>90||label.length()>44)continue;
            if(!TECH.matcher(value).find()&&!FEATURE.matcher(label).find())continue;
            if(label.matches("(?iu).*(?:цена|шифра|број\\s*на\\s*артикл).*"))continue;
            facts.add(new GoogleModelResearch.Fact(label,value,url));
        }
        return facts;
    }

    static String normalize(String raw){
        if(raw==null)return "";
        return raw.replaceAll("\\s+"," ").trim();
    }
}
