package mk.seteczaliha.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import org.json.JSONObject;
import java.io.OutputStream;

/** One product label per A4 PDF page, with generous margins and a real QR link. */
public final class PriceLabelExporter {
    private PriceLabelExporter(){}
    private static void line(Canvas c,Paint p,String text,float x,float y,int size,int color,boolean bold){
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);
        p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        c.drawText(text,x,y,p);
    }
    private static String shortText(String value,int max){
        value=value==null?"":value.replaceAll("[\\r\\n\\t]+"," ").trim();
        return value.length()>max?value.substring(0,max-1)+"…":value;
    }
    private static String price(String value){
        return value!=null&&value.matches("[0-9][0-9.,]{0,15}")?value+" ден.":"Цена: непотврдена";
    }
    public static void write(OutputStream stream,JSONObject data)throws Exception{
        String name=shortText(data.optString("name"),75),sku=shortText(data.optString("sku"),24);
        String kind=data.optString("kind","standard");
        if(!kind.equals("promo")&&!kind.equals("club"))kind="standard";
        String regular=data.optString("regular"),club=data.optString("club");
        String url=data.optString("url");
        int blue=Color.rgb(18,67,134),red=Color.rgb(191,33,36),dark=Color.rgb(31,38,49);
        PdfDocument pdf=new PdfDocument();
        PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
        Canvas c=page.getCanvas();c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        // Label content deliberately uses only part of each A4 sheet.
        float left=48,right=547,top=166,bottom=650;
        p.setColor(Color.rgb(216,222,231));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);
        c.drawRoundRect(new RectF(left,top,right,bottom),5,5,p);
        p.setStyle(Paint.Style.FILL);
        line(c,p,"Setec",73,219,36,blue,true);
        line(c,p,"НЕОФИЦИЈАЛЕН ЦЕНОВНИК",73,244,10,Color.GRAY,false);
        if(kind.equals("promo"))line(c,p,"АКЦИЈА",420,216,18,red,true);
        if(kind.equals("club"))line(c,p,"КЛУБ ЦЕНА",395,216,15,blue,true);
        p.setColor(blue);c.drawRect(73,264,522,267,p);
        // Wrap product name to at most two lines; avoid clipping long model names.
        p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(22);
        String[] words=name.split(" ");String first="",second="";
        for(String w:words){
            String trial=first.isEmpty()?w:first+" "+w;
            if(p.measureText(trial)<440||first.isEmpty())first=trial;
            else second+=(second.isEmpty()?"":" ")+w;
        }
        line(c,p,shortText(first,58),73,311,22,dark,true);
        if(!second.isEmpty())line(c,p,shortText(second,54),73,341,19,dark,true);
        line(c,p,"Шифра: "+(sku.isEmpty()?"—":sku),73,391,15,Color.DKGRAY,false);
        String primary=kind.equals("standard")?(!regular.isEmpty()?regular:club):(!club.isEmpty()?club:regular);
        if(kind.equals("promo")&&!regular.isEmpty()&&!club.isEmpty()&&!regular.equals(club)){
            line(c,p,"Редовна: "+price(regular),73,435,16,Color.GRAY,false);
        }
        if(kind.equals("club"))line(c,p,"Клуб цена",73,466,16,blue,true);
        else if(kind.equals("promo"))line(c,p,"Промотивна цена",73,466,16,red,true);
        else line(c,p,"Цена",73,466,16,blue,true);
        line(c,p,price(primary),73,520,33,kind.equals("promo")?red:blue,true);
        if(SetecPageData.isOfficialProductUrl(url)){
            com.google.zxing.common.BitMatrix qr=new com.google.zxing.qrcode.QRCodeWriter()
                .encode(url,com.google.zxing.BarcodeFormat.QR_CODE,180,180);
            float cell=108f/qr.getWidth();
            p.setColor(Color.WHITE);c.drawRect(409,483,526,600,p);
            p.setColor(Color.BLACK);
            for(int y=0;y<qr.getHeight();y++)for(int x=0;x<qr.getWidth();x++)
                if(qr.get(x,y))c.drawRect(414+x*cell,489+y*cell,414+(x+1)*cell+.1f,489+(y+1)*cell+.1f,p);
            line(c,p,"Скенирај за производ",409,616,9,Color.GRAY,false);
        }
        line(c,p,"Цените провери ги пред печатење.",73,616,10,Color.GRAY,false);
        pdf.finishPage(page);
        try{pdf.writeTo(stream);}finally{pdf.close();}
    }
}
