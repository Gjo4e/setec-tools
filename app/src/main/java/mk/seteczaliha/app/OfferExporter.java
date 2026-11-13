package mk.seteczaliha.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Export a saved customer quote in native PDF and genuine Office OpenXML (.xlsx).
 * No internet and no external apps are required to create these documents.
 */
final class OfferExporter {
    private static final Locale MK = new Locale("mk", "MK");
    private static final double ADMIN_RATE = 0.03;
    private OfferExporter() {}

    static final class Item {
        final String name, sku;
        final int qty;
        final Double club, regular;
        Item(String name, String sku, int qty, Double club, Double regular) {
            this.name=name;this.sku=sku;this.qty=qty;this.club=club;this.regular=regular;
        }
    }

    static final class Quote {
        final List<Item> items = new ArrayList<>();
        final String created = new SimpleDateFormat("dd.MM.yyyy HH:mm", MK).format(new Date());
        final int months;
        final double interestRate;
        final boolean installmentsEnabled;
        int clubMissing, regularMissing, count;
        double clubTotal, regularTotal;
        Quote(int months, double interestRate, boolean enabled) { this.months=months; this.interestRate=interestRate; this.installmentsEnabled=enabled; }
        boolean canCalculateInstallments(){return installmentsEnabled&&count>0&&clubMissing==0&&clubTotal>0;}
        double admin(){return clubTotal*ADMIN_RATE;}
        double interest(){return clubTotal*interestRate/100.0;}
        double toPay(){return clubTotal+admin()+interest();}
        double monthly(){return toPay()/months;}
    }

    static Quote parse(String json) throws JSONException {
        if(json==null||json.length()>110000)throw new JSONException("Невалидна или преголема листа.");
        JSONObject data=new JSONObject(json);
        String installment=data.optString("installment", "12");
        int months;
        double interest;
        if(installment.isEmpty()){months=0;interest=0;}
        else if("48-zero".equals(installment)){months=48;interest=0;}
        else{
            try{months=Integer.parseInt(installment);}catch(NumberFormatException e){throw new JSONException("Неважечки број на рати.");}
            switch(months){
                case 3:interest=0;break;
                case 6:interest=0;break;
                case 9:interest=0;break;
                case 12:interest=0;break;
                case 15:interest=0;break;
                case 18:interest=0;break;
                case 24:interest=0;break;
                case 36:interest=0;break;
                case 48:interest=0;break;
                case 60:interest=14.99;break;
                default:throw new JSONException("Неважечки број на рати.");
            }
        }
        JSONArray arr=data.optJSONArray("items");
        if(arr==null||arr.length()==0||arr.length()>100)throw new JSONException("Додај 1–100 производи за извоз.");
        Quote quote=new Quote(months,interest,!installment.isEmpty());
        for(int i=0;i<arr.length();i++){
            JSONObject x=arr.optJSONObject(i);
            if(x==null)throw new JSONException("Невалиден производ.");
            String name=clip(x.optString("name","").trim(),200);
            String sku=clip(x.optString("sku","").trim(),60);
            int qty=x.optInt("qty",0);
            if(name.isEmpty()||qty<1||qty>99)throw new JSONException("Неважечки назив или количина.");
            Double club=price(x,"club"),regular=price(x,"regular");
            quote.items.add(new Item(name,sku,qty,club,regular));
            quote.count+=qty;
            if(club==null)quote.clubMissing+=qty;else quote.clubTotal+=club*qty;
            if(regular==null)quote.regularMissing+=qty;else quote.regularTotal+=regular*qty;
        }
        return quote;
    }

    private static String clip(String s,int limit){return s.length()>limit?s.substring(0,limit):s;}
    private static Double price(JSONObject object,String key) throws JSONException {
        if(!object.has(key)||object.isNull(key))return null;
        Object raw=object.get(key);
        if(!(raw instanceof Number))throw new JSONException("Цената мора да биде број.");
        double n=((Number)raw).doubleValue();
        if(!Double.isFinite(n)||n<0||n>100000000)throw new JSONException("Неважечка цена.");
        return n;
    }
    private static String currency(double v){
        NumberFormat f=NumberFormat.getNumberInstance(MK);
        f.setMinimumFractionDigits(0);f.setMaximumFractionDigits(2);
        return f.format(v)+" ден.";
    }
    private static String maybeCurrency(Double v){return v==null?"Непотврдено":currency(v);}
    private static String countText(int v){return String.valueOf(v);}

    static void pdf(OutputStream stream,Quote quote) throws IOException {
        PdfDocument document=new PdfDocument();
        try{
            PdfLayout layout=new PdfLayout(document);
            layout.heading("ПОНУДА ЗА КУПУВАЧ");
            layout.text("Подготвено: "+quote.created,11,Color.rgb(95,116,139),false);
            layout.text("Број на производи: "+quote.count,10,Color.rgb(95,116,139),false);
            layout.space(14);
            for(int i=0;i<quote.items.size();i++){
                Item p=quote.items.get(i);
                layout.item(i+1,p);
            }
            layout.space(14);
            layout.heading("ВКУПНИ ИЗНОСИ");
            layout.row("Клуб цена вкупно",currency(quote.clubTotal)+(quote.clubMissing>0?" *":""));
            layout.row("Редовна цена вкупно",currency(quote.regularTotal)+(quote.regularMissing>0?" *":""));
            if(quote.clubMissing>0||quote.regularMissing>0){
                layout.paragraph("* Дел од производите немаат потврдена цена. Прикажаните збирови се делумни.",10,Color.rgb(157,87,28));
            }
            layout.space(12);
            if(quote.installmentsEnabled){
            layout.heading("ИНФОРМАТИВНА ПРЕСМЕТКА НА РАТИ");
            layout.row("Број на рати",countText(quote.months));
            layout.row("Административен трошок","3%");
            layout.row("Камата",currencyRate(quote.interestRate)+(quote.interestRate==0?" (без камата)":""));
            if(quote.canCalculateInstallments()){
                layout.row("Административен трошок (3%)",currency(quote.admin()));
                layout.row("Износ на камата",currency(quote.interest()));
                layout.row("Вкупно за плаќање",currency(quote.toPay()));
                layout.row("Месечна рата",currency(quote.monthly()));
            }else{
                layout.paragraph("Не е пресметана рата бидејќи недостасуваат клуб цени за дел од листата.",10,Color.rgb(157,87,28));
            }
            }
            layout.space(15);
            layout.paragraph("Информативна понуда. Цените и достапноста се според последно вчитаните податоци и мора да се потврдат во SETEC. Конечните услови за плаќање ги утврдува продавачот.",9,Color.rgb(91,108,125));
            layout.closePage();
            document.writeTo(stream);
        }finally{document.close();}
    }

    private static String currencyRate(double rate){
        NumberFormat f=NumberFormat.getNumberInstance(MK);
        f.setMinimumFractionDigits(0);f.setMaximumFractionDigits(2);
        return f.format(rate)+"%";
    }

    private static final class PdfLayout {
        private final PdfDocument doc;
        private PdfDocument.Page page;
        private Canvas canvas;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int pageNumber=0;
        private float y;
        private static final int WIDTH=595,HEIGHT=842;
        private static final float MARGIN=38,CONTENT=WIDTH-2*MARGIN;
        PdfLayout(PdfDocument d){doc=d;openPage();}
        private void openPage(){
            pageNumber++;
            PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,pageNumber).create();
            page=doc.startPage(info);canvas=page.getCanvas();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(12,30,49));canvas.drawRect(0,0,WIDTH,96,p);
            draw("SETEC · ПОНУДА",MARGIN,48,20,Color.WHITE,true);
            draw("Информативна понуда",MARGIN,73,10,Color.rgb(184,216,244),false);
            y=120;
        }
        void closePage(){
            if(page==null)return;
            p.setColor(Color.rgb(220,229,237));canvas.drawRect(MARGIN,HEIGHT-43,WIDTH-MARGIN,HEIGHT-42,p);
            draw("Информативна понуда",MARGIN,HEIGHT-29,9,Color.rgb(111,128,145),false);
            draw("Стр. "+pageNumber,WIDTH-MARGIN-42,HEIGHT-29,9,Color.rgb(111,128,145),false);
            doc.finishPage(page);
            page=null;
        }
        void check(float height){
            if(y+height>HEIGHT-70){closePage();openPage();}
        }
        void space(float n){check(n);y+=n;}
        void heading(String text){
            check(40);
            draw(text,MARGIN,y,13,Color.rgb(12,84,143),true);
            y+=19;
            p.setColor(Color.rgb(211,225,239));canvas.drawRect(MARGIN,y,WIDTH-MARGIN,y+1,p);
            y+=15;
        }
        void text(String text,float size,int color,boolean bold){
            check(size+8);draw(text,MARGIN,y,size,color,bold);y+=size+8;
        }
        void paragraph(String text,float size,int color){
            List<String> lines=wrap(text,CONTENT,size,false);
            for(String line:lines)text(line,size,color,false);
        }
        void row(String left,String right){
            float leftWidth=CONTENT*.60f;
            List<String> l=wrap(left,leftWidth,10,false);
            List<String> r=wrap(right,CONTENT-leftWidth,10,true);
            int n=Math.max(l.size(),r.size());
            check(n*15+9);
            for(int i=0;i<n;i++){
                if(i<l.size())draw(l.get(i),MARGIN,y+i*15,10,Color.rgb(54,76,100),false);
                if(i<r.size())draw(r.get(i),MARGIN+leftWidth,y+i*15,10,Color.rgb(15,67,108),true);
            }
            y+=n*15+9;
        }
        void item(int index,Item item){
            List<String> nameLines=wrap(index+". "+item.name,CONTENT,11,true);
            float height=nameLines.size()*16+48;
            check(height);
            for(String line:nameLines){draw(line,MARGIN,y,11,Color.rgb(20,48,78),true);y+=16;}
            draw("Шифра: "+(item.sku.isEmpty()?"—":item.sku)+"     Количина: "+item.qty,
                    MARGIN,y,9,Color.rgb(88,107,126),false);y+=16;
            draw("Клуб: "+maybeCurrency(item.club)+"     Редовна: "+maybeCurrency(item.regular),
                    MARGIN,y,9,Color.rgb(35,105,151),false);y+=14;
            if(item.qty>1){
                String club=item.club==null?"Непотврдено":currency(item.club*item.qty);
                draw("Клуб вкупно за ставка: "+club,MARGIN,y,9,Color.rgb(35,105,151),false);
                y+=13;
            }
            p.setColor(Color.rgb(232,238,244));canvas.drawRect(MARGIN,y,WIDTH-MARGIN,y+1,p);
            y+=15;
        }
        private void draw(String value,float x,float baseline,float size,int color,boolean bold){
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            p.setStyle(Paint.Style.FILL);
            canvas.drawText(value,x,baseline,p);
        }
        private List<String> wrap(String raw,float maxWidth,float size,boolean bold){
            p.setTextSize(size);
            p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            String[] words=String.valueOf(raw).split("\\s+");
            List<String> lines=new ArrayList<>();StringBuilder part=new StringBuilder();
            for(String word:words){
                if(word.isEmpty())continue;
                String tryLine=part.length()==0?word:part+" "+word;
                if(p.measureText(tryLine)<=maxWidth){part.setLength(0);part.append(tryLine);continue;}
                if(part.length()>0){lines.add(part.toString());part.setLength(0);}
                StringBuilder piece=new StringBuilder();
                for(int i=0;i<word.length();i++){
                    String candidate=piece.toString()+word.charAt(i);
                    if(piece.length()>0&&p.measureText(candidate)>maxWidth){lines.add(piece.toString());piece.setLength(0);}
                    piece.append(word.charAt(i));
                }
                part.append(piece);
            }
            if(part.length()>0)lines.add(part.toString());
            if(lines.isEmpty())lines.add("");
            return lines;
        }
    }

    static void excel(OutputStream output,Quote quote) throws IOException {
        ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8);
        try{
            zipText(zip,"[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"+
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"+
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"+
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"+
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"+
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"+
                "</Types>");
            zipText(zip,"_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"+
                "</Relationships>");
            zipText(zip,"xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "+
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"+
                "<sheets><sheet name=\"Понуда\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            zipText(zip,"xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"+
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"+
                "</Relationships>");
            zipText(zip,"xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"+
                "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.00&quot; ден.&quot;\"/></numFmts>"+
                "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"+
                "<font><b/><sz val=\"16\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"+
                "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts>"+
                "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>"+
                "<fill><patternFill patternType=\"gray125\"/></fill>"+
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF123D64\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"+
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"+
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"+
                "<cellXfs count=\"5\">"+
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"+
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFill=\"1\" applyFont=\"1\"/>"+
                "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFill=\"1\" applyFont=\"1\"/>"+
                "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"+
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"+
                "</cellXfs></styleSheet>");
            zipText(zip,"xl/worksheets/sheet1.xml",sheet(quote));
            zip.finish();
        }finally{zip.close();}
    }

    private static String sheet(Quote q){
        StringBuilder x=new StringBuilder(11000);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        x.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        x.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"3\" topLeftCell=\"A4\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        x.append("<cols><col min=\"1\" max=\"1\" width=\"6\" customWidth=\"1\"/>"+
            "<col min=\"2\" max=\"2\" width=\"54\" customWidth=\"1\"/>"+
            "<col min=\"3\" max=\"3\" width=\"18\" customWidth=\"1\"/>"+
            "<col min=\"4\" max=\"4\" width=\"13\" customWidth=\"1\"/>"+
            "<col min=\"5\" max=\"8\" width=\"21\" customWidth=\"1\"/></cols>");
        x.append("<sheetData>");
        x.append("<row r=\"1\" ht=\"30\" customHeight=\"1\">");
        textCell(x,"A1","SETEC · ПОНУДА ЗА КУПУВАЧ",1);
        x.append("</row><row r=\"2\">");
        textCell(x,"A2","Изработено: "+q.created+"   ·   Информативна понуда",0);
        x.append("</row><row r=\"3\" ht=\"28\" customHeight=\"1\">");
        String[] headers={"Рб.","Производ","Шифра","Кол.","Клуб цена","Редовна цена","Клуб вкупно","Редовна вкупно"};
        for(int c=0;c<8;c++)textCell(x,(char)('A'+c)+"3",headers[c],2);
        x.append("</row>");
        for(int i=0;i<q.items.size();i++){
            Item p=q.items.get(i);int row=i+4;
            x.append("<row r=\"").append(row).append("\" ht=\"29\" customHeight=\"1\">");
            numberCell(x,"A"+row,i+1,0);
            textCell(x,"B"+row,p.name,0);
            textCell(x,"C"+row,p.sku,0);
            numberCell(x,"D"+row,p.qty,0);
            if(p.club!=null)numberCell(x,"E"+row,p.club,3);
            if(p.regular!=null)numberCell(x,"F"+row,p.regular,3);
            if(p.club!=null)formulaCell(x,"G"+row,"D"+row+"*E"+row,3);
            if(p.regular!=null)formulaCell(x,"H"+row,"D"+row+"*F"+row,3);
            x.append("</row>");
        }
        int total=q.items.size()+4,first=4,last=total-1;
        x.append("<row r=\"").append(total).append("\">");
        textCell(x,"B"+total,"ВКУПНО",2);
        formulaCell(x,"G"+total,"SUM(G"+first+":G"+last+")",3);
        formulaCell(x,"H"+total,"SUM(H"+first+":H"+last+")",3);
        x.append("</row>");
        int note=total+2;
        rowText(x,note,"B","Непотврдена клуб цена: "+q.clubMissing+" ставки; редовна цена: "+q.regularMissing+" ставки.");
        if(q.installmentsEnabled){
            rowText(x,note+2,"B","ПРЕСМЕТКА НА РАТИ");
            x.append("<row r=\"").append(note+3).append("\">");
            textCell(x,"B"+(note+3),"Број на рати",0);
            numberCell(x,"G"+(note+3),q.months,0);
            x.append("</row>");
            x.append("<row r=\"").append(note+4).append("\">");
            textCell(x,"B"+(note+4),"Административен трошок (3%)",0);
            if(q.canCalculateInstallments())formulaCell(x,"G"+(note+4),"G"+total+"*3/100",3);
            x.append("</row>");
            x.append("<row r=\"").append(note+5).append("\">");
            textCell(x,"B"+(note+5),"Камата ("+currencyRate(q.interestRate)+")",0);
            if(q.canCalculateInstallments())formulaCell(x,"G"+(note+5),"G"+total+"*"+rateNumber(q.interestRate)+"/100",3);
            x.append("</row>");
            x.append("<row r=\"").append(note+6).append("\">");
            textCell(x,"B"+(note+6),"Вкупно за плаќање",0);
            if(q.canCalculateInstallments())formulaCell(x,"G"+(note+6),"SUM(G"+total+",G"+(note+4)+",G"+(note+5)+")",3);
            x.append("</row>");
            x.append("<row r=\"").append(note+7).append("\">");
            textCell(x,"B"+(note+7),"Месечна рата",0);
            if(q.canCalculateInstallments())formulaCell(x,"G"+(note+7),"G"+(note+6)+"/G"+(note+3),3);
            x.append("</row>");
        }
        rowText(x,note+9,"B","Цените и ратите се информативни; потврди ги конечните услови во SETEC.");
        x.append("</sheetData>");
        x.append("<mergeCells count=\"2\"><mergeCell ref=\"A1:H1\"/><mergeCell ref=\"A2:H2\"/></mergeCells>");
        x.append("<autoFilter ref=\"A3:H").append(Math.max(4,total-1)).append("\"/>");
        x.append("<pageMargins left=\"0.3\" right=\"0.3\" top=\"0.6\" bottom=\"0.6\" header=\"0.2\" footer=\"0.2\"/>");
        x.append("</worksheet>");
        return x.toString();
    }

    private static String rateNumber(double rate){return Double.toString(rate);}
    private static void rowText(StringBuilder x,int n,String col,String value){
        x.append("<row r=\"").append(n).append("\">");
        textCell(x,col+n,value,0);
        x.append("</row>");
    }
    private static void rowNumber(StringBuilder x,int n,String col,double val,int style){
        x.append("<row r=\"").append(n).append("\">");
        numberCell(x,col+n,val,style);
        x.append("</row>");
    }
    private static void rowFormula(StringBuilder x,int n,String col,String expr){
        x.append("<row r=\"").append(n).append("\">");
        formulaCell(x,col+n,expr,3);
        x.append("</row>");
    }
    private static void textCell(StringBuilder x,String cell,String value,int style){
        x.append("<c r=\"").append(cell).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
            .append(escapeXml(value)).append("</t></is></c>");
    }
    private static void numberCell(StringBuilder x,String cell,double value,int style){
        x.append("<c r=\"").append(cell).append("\" s=\"").append(style).append("\"><v>")
            .append(Double.toString(value)).append("</v></c>");
    }
    private static void formulaCell(StringBuilder x,String cell,String expression,int style){
        x.append("<c r=\"").append(cell).append("\" s=\"").append(style).append("\"><f>")
            .append(expression).append("</f></c>");
    }
    private static String escapeXml(String s){
        StringBuilder r=new StringBuilder(s.length()+16);
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='&')r.append("&amp;");
            else if(c=='<')r.append("&lt;");
            else if(c=='>')r.append("&gt;");
            else if(c=='"')r.append("&quot;");
            else if(c=='\'')r.append("&apos;");
            else if(c==9||c==10||c==13||c>=32)r.append(c);
        }
        return r.toString();
    }
    private static void zipText(ZipOutputStream zip,String path,String body)throws IOException{
        ZipEntry entry=new ZipEntry(path);
        zip.putNextEntry(entry);
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        zip.write(bytes);
        zip.closeEntry();
    }
}
