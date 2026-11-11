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
        if("photo".equals(kind)||"specs".equals(kind)||"bundle".equals(kind)){
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
            connection.setConnectTimeout(2500);connection.setReadTimeout(2500);
            connection.setInstanceFollowRedirects(false);
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
        boolean comparison="compare".equals(kind),specs="specs".equals(kind);
        rect(c,p,NAVY,20,20,575,148,12);
        rect(c,p,BLUE,20,138,575,148,0);
        text(c,p,"SETEC",42,77,37,Color.WHITE,true);
        text(c,p,bundle?"ПАКЕТ ПОНУДА":specs?"ЛИСТ СО СПЕЦИФИКАЦИИ":"ЦЕНОВНИК СО СЛИКА",
            43,112,15,Color.rgb(210,229,250),true);
        String product=data.optString("name"),sku=data.optString("sku"),url=data.optString("url");
        String price=money(validPrice(data.optString("club"))?data.optString("club"):data.optString("regular"));
        if(bundle){
            JSONObject second=data.optJSONObject("second");
            if(second==null)throw new IllegalArgumentException("Избери втор производ за пакет понуда.");
            text(c,p,"ДВА ПРОИЗВОДА ВО ЕДЕН ПАКЕТ",35,191,16,BLUE,true);
            double total=0;boolean complete=true;
            for(int i=0;i<2;i++){
                JSONObject item=i==0?data:second;
                float top=i==0?217:432;
                rect(c,p,Color.rgb(240,245,252),30,top,565,top+193,12);
                text(c,p,"ПРОИЗВОД "+(i+1),47,top+31,13,BLUE,true);
                name(c,p,item.optString("name"),47,top+67,475,19,2);
                text(c,p,"Шифра: "+clean(item.optString("sku"),22),47,top+120,13,MUTED,false);
                String amount=validPrice(item.optString("club"))?item.optString("club"):item.optString("regular");
                text(c,p,money(amount),47,top+169,24,BLUE,true);
                if(validPrice(amount)){
                    try{total+=Double.parseDouble(amount.replace(".","").replace(",","."));}
                    catch(Exception ex){complete=false;}
                }else complete=false;
            }
            rect(c,p,NAVY,30,653,565,772,12);
            text(c,p,"ВКУПНО ЗА ПАКЕТОТ",49,688,16,Color.WHITE,true);
            String sum=complete?String.format(java.util.Locale.US,"%,.0f ден.",total).replace(",","." ):"Цена непотврдена";
            text(c,p,sum,49,741,35,Color.WHITE,true);
            text(c,p,"Збир на цените без дополнителен попуст.",35,806,11,MUTED,false);
        }else if(specs){
            text(c,p,"ПОДАТОЦИ ЗА ПРОИЗВОДОТ",36,196,14,BLUE,true);
            name(c,p,product,36,244,520,23,3);
            rect(c,p,Color.rgb(239,244,250),30,350,565,416,10);
            text(c,p,"ШИФРА",49,377,12,MUTED,true);
            text(c,p,clean(sku,24),49,403,21,INK,true);
            text(c,p,"КАРАКТЕРИСТИКИ",36,463,15,BLUE,true);
            String[] lines=data.optString("specs","").split("\\n");
            int y=499;
            for(String entry:lines){
                entry=clean(entry,82);
                if(entry.isEmpty())continue;
                if(y>673)break;
                text(c,p,"• "+entry,43,y,15,INK,false);y+=32;
            }
            if(data.optString("specs").trim().isEmpty())
                text(c,p,"Не се внесени потврдени спецификации.",43,503,15,MUTED,false);
            text(c,p,price,40,742,28,BLUE,true);
            qr(c,p,url,444,682,97);
        }else{
            text(c,p,"ПРОИЗВОД",36,191,13,BLUE,true);
            name(c,p,product,36,237,520,24,3);
            rect(c,p,Color.rgb(244,247,251),35,337,560,598,12);
            photo(c,data.optString("img"),48,350,548,584);
            rect(c,p,Color.rgb(234,244,255),30,618,565,779,13);
            text(c,p,"Шифра: "+clean(sku,24),49,649,14,MUTED,true);
            text(c,p,price,48,726,38,BLUE,true);
            qr(c,p,url,432,647,105);
        }
        pdf.finishPage(page);
        try{pdf.writeTo(stream);}finally{pdf.close();}
    }

}
