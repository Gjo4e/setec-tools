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
    private static int roundedDiscount(String regular,String discounted){
        if(!validPrice(regular)||!validPrice(discounted))return 0;
        try{
            double old=Double.parseDouble(regular.replace(".","").replace(",","."));
            double now=Double.parseDouble(discounted.replace(".","").replace(",","."));
            if(old<=0||now<=0||now>=old)return 0;
            double actual=(old-now)*100.0/old;
            return Math.min(100,(int)(Math.ceil((actual-0.000001)/5.0)*5));
        }catch(Exception ex){return 0;}
    }
    private static void discount(Canvas c,Paint p,String regular,String discounted,float x,float y){
        int pct=roundedDiscount(regular,discounted);
        if(pct<=0)return;
        rect(c,p,RED,x,y,x+154,y+39,8);
        text(c,p,"ДО "+pct+"% ПОПУСТ",x+12,y+26,16,Color.WHITE,true);
    }
    public static void write(OutputStream stream,JSONObject data)throws Exception{
        String name=clean(data.optString("name"),140);
        String sku=clean(data.optString("sku"),24);
        String kind=data.optString("kind","standard");
        if(!"promo".equals(kind)&&!"club".equals(kind)&&!"photo".equals(kind)&&!"specs".equals(kind))kind="promo";
        String regular=data.optString("regular"),club=data.optString("club");
        String url=data.optString("url");
        if("photo".equals(kind)||"specs".equals(kind)){
            writeSpecial(stream,data,kind);
            return;
        }
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

        discount(c,p,regular,primary,386,527);
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
    private static void qr(Canvas c,Paint p,String url,float x,float y,float size)throws Exception{
        if(!SetecPageData.isOfficialProductUrl(url))return;
        com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter()
            .encode(url,com.google.zxing.BarcodeFormat.QR_CODE,200,200);
        float step=size/matrix.getWidth();
        rect(c,p,Color.WHITE,x-5,y-5,x+size+5,y+size+5,4);
        p.setColor(Color.BLACK);
        for(int row=0;row<matrix.getHeight();row++)for(int col=0;col<matrix.getWidth();col++)
            if(matrix.get(col,row))c.drawRect(x+col*step,y+row*step,
                x+(col+1)*step+.04f,y+(row+1)*step+.04f,p);
    }
    private static void name(Canvas c,Paint p,String value,float x,float y,float width,int font,int maxLines){
        p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(font);
        ArrayList<String> lines=wrap(p,clean(value,160),width,maxLines);
        for(String row:lines){text(c,p,clean(row,66),x,y,font,INK,true);y+=font+9;}
    }
    private static void photo(Canvas c,String url,float l,float t,float r,float b){
        try{
            java.net.URL u=new java.net.URL(url);
            String host=u.getHost().toLowerCase(java.util.Locale.ROOT);
            if(!"https".equalsIgnoreCase(u.getProtocol())||
                !(host.equals("setec.mk")||host.equals("www.setec.mk")||
                  host.endsWith(".digitaloceanspaces.com")))return;
            java.net.HttpURLConnection connection=(java.net.HttpURLConnection)u.openConnection();
            connection.setConnectTimeout(6500);connection.setReadTimeout(6500);
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (Android; Setec Tools)");
            connection.setInstanceFollowRedirects(true);
            try(java.io.InputStream in=connection.getInputStream()){
                android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(in);
                if(bitmap==null)return;
                Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                float scale=Math.min((r-l)/bitmap.getWidth(),(b-t)/bitmap.getHeight());
                float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
                c.drawBitmap(bitmap,null,new RectF(l+(r-l-w)/2,t+(b-t-h)/2,l+(r-l+w)/2,t+(b-t+h)/2),p);
                bitmap.recycle();
            }finally{connection.disconnect();}
        }catch(Exception ignored){}
    }
    private static void writeSpecial(OutputStream stream,JSONObject data,String kind)throws Exception{
        PdfDocument pdf=new PdfDocument();
        PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
        Canvas c=page.getCanvas();c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean specs="specs".equals(kind);
        rect(c,p,NAVY,20,20,575,148,12);
        rect(c,p,BLUE,20,138,575,148,0);
        text(c,p,"SETEC",42,77,37,Color.WHITE,true);
        text(c,p,specs?"ЛИСТ СО СПЕЦИФИКАЦИИ":"ЦЕНОВНИК СО СЛИКА",
            43,112,15,Color.rgb(210,229,250),true);
        String product=data.optString("name"),sku=data.optString("sku"),url=data.optString("url");
        String price=money(validPrice(data.optString("club"))?data.optString("club"):data.optString("regular"));
        if(specs){
            text(c,p,"ПОДАТОЦИ ЗА ПРОИЗВОДОТ",36,196,14,BLUE,true);
            name(c,p,product,36,244,520,23,3);
            rect(c,p,Color.rgb(239,244,250),36,350,226,394,8);
            text(c,p,"ШИФРА",48,367,10,MUTED,true);
            text(c,p,clean(sku,24),48,386,16,INK,true);
            text(c,p,"КАРАКТЕРИСТИКИ",36,435,15,BLUE,true);
            String[] lines=data.optString("specs","").split("\\n");
            int y=467;
            for(String entry:lines){
                entry=clean(entry,82);
                if(entry.isEmpty())continue;
                if(y>657)break;
                p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(13);
                for(String line:wrap(p,"• "+entry,245,2)){
                    if(y>657)break;
                    text(c,p,line,43,y,13,INK,false);y+=21;
                }
                y+=7;
            }
            if(data.optString("specs").trim().isEmpty())
                text(c,p,"Не се внесени потврдени спецификации.",43,467,12,MUTED,false);
            photo(c,data.optString("img"),300,405,565,675);
            discount(c,p,data.optString("regular"),data.optString("club"),269,705);
            text(c,p,price,40,742,28,BLUE,true);
            qr(c,p,url,444,682,97);
        }else{
            text(c,p,"ПРОИЗВОД",36,191,13,BLUE,true);
            name(c,p,product,36,237,520,24,3);
            photo(c,data.optString("img"),35,315,560,602);
            rect(c,p,Color.rgb(234,244,255),30,618,565,779,13);
            text(c,p,"Шифра: "+clean(sku,24),49,649,14,MUTED,true);
            discount(c,p,data.optString("regular"),data.optString("club"),48,736);
            text(c,p,price,48,726,38,BLUE,true);
            qr(c,p,url,432,647,105);
        }
        pdf.finishPage(page);
        try{pdf.writeTo(stream);}finally{pdf.close();}
    }

}
