package mk.seteczaliha.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Produces up to five meaningful side-by-side rows.
 *
 * Named specifications are aligned by label. When a Setec product only has
 * original technical bullets, its original bullet text is still shown in
 * numbered rows. Numbered bullets are NOT presented as equivalent facts,
 * and therefore must not be marked as factual differences.
 */
final class SetecCompareRows {
    static final int MAX_ROWS=5;
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

    static List<Row> build(String leftName,Map<String,String> leftFacts,
                           List<String> leftDetails,String rightName,
                           Map<String,String> rightFacts,List<String> rightDetails){
        List<Row> rows=new ArrayList<>();
        List<String> keys=ComparisonSummary.topSpecs(
            leftName,leftFacts,rightName,rightFacts);
        for(String key:keys){
            if(rows.size()>=MAX_ROWS)break;
            rows.add(new Row(key,known(leftFacts,key),known(rightFacts,key),true));
        }
        List<String> l=remaining(leftDetails,leftFacts);
        List<String> r=remaining(rightDetails,rightFacts);
        int max=Math.max(l.size(),r.size());
        for(int i=0;i<max&&rows.size()<MAX_ROWS;i++){
            rows.add(new Row("Опис од Setec · "+(i+1),
                i<l.size()?l.get(i):"Не е наведено",
                i<r.size()?r.get(i):"Не е наведено",false));
        }
        return rows;
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
