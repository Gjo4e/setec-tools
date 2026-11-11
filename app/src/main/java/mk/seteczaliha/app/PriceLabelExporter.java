package mk.seteczaliha.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import org.json.JSONObject;
import java.io.OutputStream;
import java.util.ArrayList;

/** Print-ready A4 single-product price poster. */
public final class PriceLabelExporter {
    private PriceLabelExporter() {}
    private static final int NAVY=Color.rgb(19,38,70);
    private static final int BLUE=Color.rgb(29,100,190);
    private static final int RED=Color.rgb(201,38,47);
    private static final int INK=Color.rgb(27,39,56);
    private static final int MUTED=Color.rgb(106,119,136);
    private static void rect(Canvas c,Paint p,int color,float l,float t,float r,float b,float radius){
        p.setStyle(Paint.Style.FILL);p.setColor(color);
        c.drawRoundRect(new RectF(l,t,r,b),radius,radius,p);
    }
    private static void text(Canvas c,Paint p,String s,float x,float y,float size,int color,boolean bold){
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        c.drawText(s,x,y,p);
    }
    private static String clean(String s,int max){
        s=s==null?"":s.replaceAll("[\\r\\n\\t]+"," ").trim();
        return s.length()>max?s.substring(0,max-1)+"…":s;
    }
    private static boolean validPrice(String s){return s!=null&&s.matches("[0-9][0-9.,]{0,15}");}
    private static String money(String s){return validPrice(s)?s+" ден.":"Цена непотврдена";}
    private static ArrayList<String> wrap(Paint p,String name,float maxWidth,int maxLines){
        ArrayList<String> lines=new ArrayList<>();
        String current="";
        for(String word:name.split("\\s+")){
            String trial=current.isEmpty()?word:current+" "+word;
            if(p.measureText(trial)<=maxWidth){current=trial;}
            else {
                if(!current.isEmpty())lines.add(current);
                current=word;
                if(lines.size()==maxLines-1)break;
            }
        }
        if(!current.isEmpty()&&lines.size()<maxLines)lines.add(current);
        return lines;
    }
    public static void write(OutputStream stream,JSONObject data)throws Exception{
        String name=clean(data.optString("name"),140);
        String sku=clean(data.optString("sku"),24);
        String kind=data.optString("kind","standard");
        if(!"promo".equals(kind)&&!"club".equals(kind))kind="standard";
        String regular=data.optString("regular"),club=data.optString("club");
        String url=data.optString("url");
        boolean promo="promo".equals(kind),membership="club".equals(kind);
        int accent=promo?RED:BLUE;
        String primary=promo||membership?(validPrice(club)?club:regular):(validPrice(regular)?regular:club);
        String heading=promo?"СПЕЦИЈАЛНА ПОНУДА":membership?"КЛУБ ЦЕНА":"ЦЕНА НА ПРОИЗВОД";
        PdfDocument pdf=new PdfDocument();
        PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
        Canvas c=page.getCanvas();c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        // Strong full-width header, but retain 20pt printer-safe margins.
        rect(c,p,NAVY,20,20,575,151,13);
        rect(c,p,accent,20,137,575,151,0);
        text(c,p,"SETEC",43,80,39,Color.WHITE,true);
        text(c,p,"ЦЕНОВНИ ЕТИКЕТИ",44,112,12,Color.rgb(203,219,239),false);
        rect(c,p,accent,365,48,551,98,9);
        text(c,p,promo?"АКЦИЈА":membership?"КЛУБ ЦЕНА":"РЕДОВНА ЦЕНА",
            promo?402:membership?388:377,80,promo?21:17,Color.WHITE,true);

        text(c,p,"ПРОИЗВОД",40,204,13,accent,true);
        p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        p.setTextSize(26);
        ArrayList<String> title=wrap(p,name,510,3);
        float y=250;
        for(String row:title){text(c,p,clean(row,65),40,y,26,INK,true);y+=37;}
        rect(c,p,Color.rgb(239,243,249),40,367,555,414,8);
        text(c,p,"ШИФРА",56,397,12,MUTED,true);
        text(c,p,sku.isEmpty()?"—":sku,135,398,18,INK,true);

        // Price block is the visual focal point.
        rect(c,p,promo?Color.rgb(255,242,242):Color.rgb(237,246,255),30,444,565,662,14);
        rect(c,p,accent,30,444,39,662,0);
        text(c,p,heading,55,488,17,accent,true);
        if(promo&&validPrice(regular)&&validPrice(club)&&!regular.equals(club)){
            text(c,p,"Редовна: "+money(regular),55,526,17,MUTED,false);
        } else if(membership&&validPrice(regular)&&!regular.equals(club)){
            text(c,p,"Редовна: "+money(regular),55,526,17,MUTED,false);
        }
        String value=money(primary);
        p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        float priceSize=validPrice(primary)?52:25;
        p.setTextSize(priceSize);
        while(p.measureText(value)>474&&priceSize>24){priceSize-=2;p.setTextSize(priceSize);}
        text(c,p,value,55,615,priceSize,accent,true);

        // Bottom row: real QR to the official product, never a placeholder.
        text(c,p,"ПРОВЕРИ ГО ПРОИЗВОДОТ",42,710,13,INK,true);
        text(c,p,"Скенирај го QR-кодот за детали",42,737,12,MUTED,false);
        if(SetecPageData.isOfficialProductUrl(url)){
            com.google.zxing.common.BitMatrix qr=new com.google.zxing.qrcode.QRCodeWriter()
                .encode(url,com.google.zxing.BarcodeFormat.QR_CODE,220,220);
            int count=qr.getWidth();
            float size=118f,cell=size/count;
            rect(c,p,Color.WHITE,421,670,551,800,7);
            p.setColor(Color.BLACK);
            for(int yy=0;yy<count;yy++)for(int xx=0;xx<count;xx++)
                if(qr.get(xx,yy))c.drawRect(427+xx*cell,676+yy*cell,
                    427+(xx+1)*cell+.05f,676+(yy+1)*cell+.05f,p);
        }
        pdf.finishPage(page);
        try{pdf.writeTo(stream);}finally{pdf.close();}
    }
}
