package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Produces all available meaningful side-by-side rows.
 *
 * Named specifications are aligned by label. When a Setec product only has
 * original technical bullets, its original bullet text is still shown in
 * numbered rows. Numbered bullets are NOT presented as equivalent facts,
 * and therefore must not be marked as factual differences.
 */
final class SetecCompareRows {
    static final int MAX_ROWS=150;
    private SetecCompareRows(){}

    static final class Row {
        final String label,left,right;
        final boolean comparable;
        Row(String label,String left,String right,boolean comparable){
            this.label=label;
            this.left=left;
            this.right=right;
            this.comparable=comparable;
        }
    }

    /**
     * Only align the SAME verified, named feature on both products. Original
     * product bullets are shown separately in the two per-model highlight
     * cards; matching bullet index never means matching property.
     */
    static List<Row> build(String leftName,Map<String,String> leftFacts,
                           List<String> leftDetails,String rightName,
                           Map<String,String> rightFacts,List<String> rightDetails){
        List<Row> rows=new ArrayList<>();
        if(!CompareCategoryGuard.canCompare(leftName,rightName))return rows;
        Map<String,String> combinedLeft=new java.util.LinkedHashMap<>();
        Map<String,String> combinedRight=new java.util.LinkedHashMap<>();
        if(leftFacts!=null)for(Map.Entry<String,String> entry:leftFacts.entrySet()){String key=canonicalLabel(entry.getKey());if(!key.isEmpty())combinedLeft.put(key,entry.getValue());}
        if(rightFacts!=null)for(Map.Entry<String,String> entry:rightFacts.entrySet()){String key=canonicalLabel(entry.getKey());if(!key.isEmpty())combinedRight.put(key,entry.getValue());}
        addDescriptionFacts(combinedLeft,leftDetails);
        addDescriptionFacts(combinedRight,rightDetails);
        Map<String,String> sharedLeft=new java.util.LinkedHashMap<>();
        Map<String,String> sharedRight=new java.util.LinkedHashMap<>();
        for(Map.Entry<String,String> spec:combinedLeft.entrySet()){
            String label=spec.getKey(),left=spec.getValue(),right=combinedRight.get(label);
            if(label==null||left==null||right==null)continue;
            if(left.trim().isEmpty()||right.trim().isEmpty())continue;
            if(!compatibleUnits(label,left,right))continue;
            sharedLeft.put(label,left);
            sharedRight.put(label,right);
        }
        List<String> keys=ComparisonSummary.topSpecs(
            leftName,sharedLeft,rightName,sharedRight);
        for(String key:keys){
            if(rows.size()>=MAX_ROWS)break;
            rows.add(new Row(key,known(sharedLeft,key),known(sharedRight,key),true));
        }
        // Compare identical verified description bullets when the catalog does not
        // expose structured fields. Never infer a difference from bullet position.
        if(rows.isEmpty()&&leftDetails!=null&&rightDetails!=null){
            java.util.Map<String,String> rightBullets=new java.util.LinkedHashMap<>();
            for(String bullet:rightDetails){
                String clean=normalize(bullet);
                if(clean.length()>=4&&!isUiOrSalesText(clean))rightBullets.put(clean.toLowerCase(Locale.ROOT),clean);
            }
            for(String bullet:leftDetails){
                String clean=normalize(bullet);
                if(clean.length()<4||isUiOrSalesText(clean))continue;
                String other=rightBullets.get(clean.toLowerCase(Locale.ROOT));
                if(other==null)continue;
                rows.add(new Row("Заедничка карактеристика "+(rows.size()+1),clean,other,true));
                if(rows.size()>=MAX_ROWS)break;
            }
        }
        return rows;
    }

    /** Normalize comparable labels across Macedonian and English descriptions. */
    private static String canonicalLabel(String raw){
        String key=normalize(raw).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]","").trim();
        if(key.matches("processor|cpu|процесор|тип на процесор"))return "Процесор";
        if(key.matches("ram|меморија|работна меморија|ram memory"))return "RAM";
        if(key.matches("ssd|storage|диск|хард диск|простор за складирање|капацитет на диск"))return "Диск";
        if(key.matches("gpu|graphics|графичка|графичка картичка|video card"))return "Графичка";
        if(key.matches("display|екран|дисплеј|големина на екран"))return "Екран";
        if(key.matches("battery|батерија|траење на батерија"))return "Батерија";
        if(key.matches("bluetooth|блутут"))return "Bluetooth";
        if(key.matches("ports|порти|приклучоци"))return "Порти";
        if(key.matches("resolution|резолуција"))return "Резолуција";
        if(key.matches("refresh rate|освежување|фреквенција на освежување"))return "Освежување";
        if(key.matches("capacity|капацитет"))return "Капацитет";
        if(key.matches("energy class|енергетска класа"))return "Енергетска класа";
        if(key.matches("power|моќност|снага"))return "Моќност";
        if(key.matches("dimensions|димензии"))return "Димензии";
        if(key.matches("weight|тежина"))return "Тежина";
        if(key.matches("noise|бучава"))return "Бучава";
        if(key.matches("warranty|гаранција"))return "";
        return key.length()>=3&&key.length()<=45?normalize(raw):"";
    }

    /** Read generic named specifications, not just laptop-specific fields. */
    private static void addDescriptionFacts(Map<String,String> facts,List<String> details){
        if(details==null)return;
        for(String raw:details){
            String line=normalize(raw).replaceFirst("^[•\\-]+\\s*","");
            java.util.regex.Matcher m=Pattern.compile("^([^:：]{3,48})\\s*[:：]\\s*(.{2,180})$").matcher(line);
            if(!m.matches()){
                // Setec also writes "- Процесор AMD Ryzen ..." without a colon.
                m=Pattern.compile("(?iu)^(Процесор|Processor|CPU|RAM|Меморија|Диск|SSD|Storage|Графичка|Graphics|GPU|Екран|Display|Батерија|Battery|Bluetooth|Ports|Порти|Резолуција|Resolution|Капацитет|Capacity)\\s+(.{2,180})$").matcher(line);
                if(!m.matches())continue;
            }
            String label=canonicalLabel(m.group(1)),value=m.group(2).trim();
            if(label.isEmpty()||isUiOrSalesText(label)||value.length()>180)continue;
            facts.putIfAbsent(label,value);
        }
    }

    /** Disallow matching "8 kg" to "8 L", "1200 rpm" or "512 GB". */
    static boolean compatibleUnits(String label,String left,String right){
        String u=unitFamily(left),v=unitFamily(right);
        if(!u.equals(v))return false;
        if(label!=null&&label.trim().equalsIgnoreCase("Капацитет"))
            return !u.isEmpty(); // Plain "капацитет" without units is ambiguous.
        return true;
    }

    private static String unitFamily(String source){
        String s=String.valueOf(source).toLowerCase(Locale.ROOT);
        if(s.matches(".*\\b(?:кг|kg|килограми?)\\b.*"))return "WEIGHT";
        if(s.matches(".*\\b(?:литри?|liters?|litres?|l)\\b.*"))return "VOLUME";
        if(s.matches(".*\\b(?:tb|gb|mb|тб|гб)\\b.*"))return "STORAGE";
        if(s.matches(".*\\b(?:rpm|вртежи)\\b.*"))return "RPM";
        if(s.matches(".*\\b(?:ghz|mhz)\\b.*"))return "FREQUENCY";
        if(s.matches(".*\\b(?:hz)\\b.*"))return "HZ";
        if(s.matches(".*\\b(?:btu)\\b.*"))return "BTU";
        if(s.matches(".*\\b(?:db|дб)\\b.*"))return "DECIBEL";
        if(s.matches(".*\\b(?:w|kw|ват(?:и)?)\\b.*"))return "WATTS";
        if(s.matches(".*\\b(?:mah|wh)\\b.*"))return "BATTERY";
        if(s.matches(".*\\b(?:mm|cm|мм|см)\\b.*"))return "LENGTH";
        return "";
    }

    /**
     * Per-product top five. Unlike a comparison union, these rows can be shown
     * as soon as ONE SKU has been verified. Source text shown in the scanner is
     * included as a fallback, so the comparison never discards visible specs.
     */
    static List<String> highlights(String model,Map<String,String> facts,
                                   List<String> original,String displayed){
        List<String> out=new ArrayList<>();
        Map<String,String> safe=facts==null?java.util.Collections.emptyMap():facts;
        List<String> keys=ComparisonSummary.topSpecs(
            model,safe,"",java.util.Collections.emptyMap());
        for(String key:keys){
            String val=safe.get(key);
            if(val==null)continue;
            String line=normalize(key)+": "+normalize(val);
            if(!isUiOrSalesText(line)&&line.length()>=5&&line.length()<=230)
                out.add(line);
            if(out.size()>=MAX_ROWS)return out;
        }
        List<String> bullets=new ArrayList<>();
        if(original!=null)bullets.addAll(original);
        bullets.addAll(parseLines(displayed));
        for(String line:remaining(bullets,safe)){
            if(out.size()>=MAX_ROWS)break;
            if(!out.contains(line))out.add(line);
        }
        return out;
    }

    private static String known(Map<String,String> facts,String key){
        if(facts==null)return "Непотврдено";
        String val=facts.get(key);
        return val==null||val.trim().isEmpty()?"Непотврдено":val.trim();
    }

    private static List<String> remaining(List<String> details,Map<String,String> facts){
        List<String> result=new ArrayList<>();
        if(details==null)return result;
        Set<String> seen=new LinkedHashSet<>();
        for(String raw:details){
            String line=normalize(raw);
            if(line.length()<3||line.length()>230||!seen.add(line.toLowerCase(Locale.ROOT)))
                continue;
            if(isUiOrSalesText(line))continue;
            boolean duplicate=false;
            if(facts!=null){
                for(Map.Entry<String,String> f:facts.entrySet()){
                    String val=normalize(f.getValue()).toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+","");
                    String compact=line.toLowerCase(Locale.ROOT).replaceAll("\\s+","");
                    if(val.length()>=4&&compact.contains(val)){
                        duplicate=true;break;
                    }
                }
            }
            if(!duplicate)result.add(line);
        }
        return result;
    }

    static List<String> parseLines(String description){
        List<String> result=new ArrayList<>();
        if(description==null)return result;
        for(String raw:description.split("\\r?\\n")){
            String line=normalize(raw);
            if(line.startsWith("•"))line=normalize(line.substring(1));
            if(line.length()<3||line.length()>230||isUiOrSalesText(line))continue;
            if(!result.contains(line))result.add(line);
            if(result.size()>=MAX_ROWS)break;
        }
        return result;
    }

    static String asDescription(List<String> lines){
        if(lines==null)return "";
        StringBuilder out=new StringBuilder();
        for(String line:lines){
            String clean=normalize(line);
            if(clean.length()<3||clean.length()>230||isUiOrSalesText(clean))continue;
            if(out.length()>0)out.append('\n');
            out.append("• ").append(clean);
            if(out.length()>=800)break;
        }
        return out.toString().substring(0,Math.min(800,out.length()));
    }

    static boolean isUiOrSalesText(String line){
        if(line==null)return true;
        return Pattern.compile("(?iu)(?:^(?:НАЈВАЖНИ|ОРИГИНАЛЕН|"+
            "МОДЕЛОТ И ЦЕНАТА|ТЕХНИЧКИТЕ|Извор:|Се вчитуваат)|"+
            "клуб\\s*цена|редовна\\s*цена|ден(?:ари)?\\b|\\bмкд\\b|"+
            "шифра\\s*:|гаранција|достава|кошничка|cashback|"+
            "слични\\s*производи|поврзани\\s*производи)")
            .matcher(line).find();
    }

    private static String normalize(String s){
        if(s==null)return "";
        return s.replaceAll("\\s+"," ").trim();
    }
}
