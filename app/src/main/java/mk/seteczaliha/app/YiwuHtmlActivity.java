package mk.seteczaliha.app;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Open a user-selected offline HTML workspace without exposing the app's Setec bridge. */
public final class YiwuHtmlActivity extends ComponentActivity {
    private WebView viewer;
    private ActivityResultLauncher<String[]> picker;
    private static final String PREFS="yiwu_html";
    private static final String URI_KEY="selected_uri";

    @Override @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8,13,20));
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);
        Button choose=new Button(this);
        choose.setText("Избери SETEC HELP YIWU HTML");
        layout.addView(choose,new LinearLayout.LayoutParams(-1,-2));
        viewer=new WebView(this);
        WebSettings settings=viewer.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        viewer.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest request){
                if(!request.getUrl().toString().startsWith("https://appassets.androidplatform.net/")){
                    return true;
                }
                return false;
            }
        });
        layout.addView(viewer,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(layout);
        picker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
            if(uri==null)return;
            try{
                getContentResolver().takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }catch(Exception ignored){}
            getPreferences(MODE_PRIVATE).edit().putString(URI_KEY,uri.toString()).apply();
            openHtml(uri);
        });
        choose.setOnClickListener(v->picker.launch(new String[]{"text/html","application/xhtml+xml","application/octet-stream"}));
        String saved=getPreferences(MODE_PRIVATE).getString(URI_KEY,"");
        if(!saved.isEmpty())openHtml(Uri.parse(saved));
        else Toast.makeText(this,"Првпат избери го YIWU HTML фајлот преку копчето горе.",Toast.LENGTH_LONG).show();
    }
    private void openHtml(Uri uri){
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new IllegalStateException("Empty file");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[16384];int n;
            while((n=in.read(buffer))!=-1){
                if(bytes.size()+n>80*1024*1024)throw new IllegalStateException("File too large");
                bytes.write(buffer,0,n);
            }
            String html=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            if(!html.toLowerCase(java.util.Locale.ROOT).contains("<html"))
                throw new IllegalArgumentException("Not an HTML file");
            viewer.loadDataWithBaseURL("https://appassets.androidplatform.net/yiwu/",""+html,"text/html","UTF-8",null);
        }catch(Exception ex){
            Toast.makeText(this,"Не може да се отвори HTML. Избери го фајлот повторно.",Toast.LENGTH_LONG).show();
            getPreferences(MODE_PRIVATE).edit().remove(URI_KEY).apply();
        }
    }
    @Override public void onBackPressed(){
        if(viewer.canGoBack())viewer.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        viewer.destroy();
        super.onDestroy();
    }
}
