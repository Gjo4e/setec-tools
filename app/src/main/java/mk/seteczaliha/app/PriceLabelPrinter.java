package mk.seteczaliha.app;

import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.PageRange;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import android.app.Activity;
import android.widget.Toast;

/** Sends the same single-page A4 PDF used by export to Android's native print dialog. */
public final class PriceLabelPrinter {
    private PriceLabelPrinter(){}
    public static void print(Context context,JSONObject data){
        PrintManager manager=(PrintManager)context.getSystemService(Context.PRINT_SERVICE);
        if(manager==null)throw new IllegalStateException("Печатењето не е достапно.");
        PrintAttributes attributes=new PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build();
        String sku=data.optString("sku","proizvod").replaceAll("[^0-9A-Za-z_-]","");
        Activity activity=(Activity)context;
        Toast.makeText(context,"Се подготвува ценовникот…",Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ByteArrayOutputStream pdf=new ByteArrayOutputStream();
                PriceLabelExporter.write(pdf,data);
                byte[] bytes=pdf.toByteArray();
                activity.runOnUiThread(() -> {
                    if(activity.isFinishing() || activity.isDestroyed())return;
                    try {
                        openPrint(manager,attributes,sku,bytes);
                    } catch(Exception ex) {
                        Toast.makeText(context,"Печатењето не успеа: "+ex.getMessage(),Toast.LENGTH_LONG).show();
                    }
                });
            } catch(Exception ex) {
                activity.runOnUiThread(() -> Toast.makeText(context,"Не може да се подготви ценовникот: "+ex.getMessage(),Toast.LENGTH_LONG).show());
            }
        },"price-label-print").start();
    }
    private static void openPrint(PrintManager manager,PrintAttributes attributes,String sku,byte[] bytes){
        manager.print("Setec-Cenovnik-"+sku,new PrintDocumentAdapter(){
            @Override public void onLayout(PrintAttributes oldAttributes,PrintAttributes newAttributes,
                    CancellationSignal cancellation,LayoutResultCallback callback,Bundle extras){
                if(cancellation.isCanceled()){callback.onLayoutCancelled();return;}
                PrintDocumentInfo info=new PrintDocumentInfo.Builder("Setec-Cenovnik-"+sku+".pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build();
                callback.onLayoutFinished(info,true);
            }
            @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,
                    CancellationSignal cancellation,WriteResultCallback callback){
                if(cancellation.isCanceled()){
                    try{destination.close();}catch(Exception ignored){}
                    callback.onWriteCancelled();return;
                }
                try(FileOutputStream stream=new FileOutputStream(destination.getFileDescriptor())){
                    stream.write(bytes);
                    stream.flush();
                    if(cancellation.isCanceled())callback.onWriteCancelled();
                    else callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                }catch(Exception ex){
                    callback.onWriteFailed("Печатењето не успеа: "+ex.getMessage());
                }finally{
                    try{destination.close();}catch(Exception ignored){}
                }
            }
        },attributes);
    }
}
