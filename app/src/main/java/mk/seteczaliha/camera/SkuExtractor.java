package mk.seteczaliha.camera;
import java.util.*;
import java.util.regex.*;

public final class SkuExtractor {
  private SkuExtractor(){}
  private static final Pattern TAG=Pattern.compile("(?iu)(?:шифра|shifra|sifra|šifra)\\s*[:：.]?\\s*(\\d{4,7})(?!\\d)");
  private static final Pattern LABEL=Pattern.compile("(?iu)(?:шифра|shifra|sifra|šifra)");
  private static final Pattern NUM=Pattern.compile("(?<!\\d)\\d{4,7}(?!\\d)");
  public static String extract(String text){
    if(text==null||text.trim().isEmpty())return null;
    text=text.replace('\u00a0',' ');
    Matcher m=TAG.matcher(text);
    if(m.find())return m.group(1);
    String[] lines=text.split("[\\r\\n]+");
    for(int i=0;i<lines.length;i++)if(LABEL.matcher(lines[i]).find()){
      for(int j=i+1;j<lines.length&&j<=i+2;j++){
        m=NUM.matcher(lines[j]);
        if(m.find())return m.group();
      }
    }
    Set<String> possible=new HashSet<>();
    for(String line:lines){
      if(line.toLowerCase(Locale.ROOT).matches(".*(цена|ден\\.|price|мкд).*"))continue;
      m=NUM.matcher(line);
      while(m.find())if(m.group().length()==5)possible.add(m.group());
    }
    return possible.size()==1?possible.iterator().next():null;
  }
}
