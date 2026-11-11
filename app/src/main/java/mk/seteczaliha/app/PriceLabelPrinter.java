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
                    PriceLabelExporter.write(stream,data);
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
