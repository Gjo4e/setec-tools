package mk.seteczaliha.camera;

import android.app.*;
import android.os.*;
import android.provider.MediaStore;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;
import java.io.IOException;
import org.json.JSONObject;

public final class MainActivity extends Activity {
  private static final String HOME="https://www.setec.mk/";
  private static final int PHOTO=42;
  private static final int DARK=Color.rgb(29,28,32), ORANGE=Color.rgb(255,137,27);
  private WebView web;
  private EditText query;
  private TextView hint;
  private ProgressBar progress;
  private Uri photoUri;
  private String pending;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

  private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
  private TextView label(String s,int size,int color){
    TextView v=new TextView(this);
    v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }
  private Button button(String s){
    Button b=new Button(this);b.setText(s);b.setAllCaps(false);
    b.setTextColor(Color.WHITE);b.setTextSize(13);
    b.setBackgroundTintList(ColorStateList.valueOf(ORANGE));
    return b;
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(DARK);
    getWindow().setNavigationBarColor(Color.BLACK);
    LinearLayout layout=new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setBackgroundColor(Color.rgb(248,248,249));
    setContentView(layout);

    LinearLayout toolbar=new LinearLayout(this);
    toolbar.setBackgroundColor(DARK);toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.setPadding(dp(12),dp(6),dp(12),dp(6));
    layout.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(66)));
    TextView title=label("Setec Залиха",21,Color.WHITE);
    title.setTypeface(null,1);
    toolbar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
    Button scan=button("📷 Скенирај");
    toolbar.addView(scan,new LinearLayout.LayoutParams(-2,dp(53)));
    scan.setOnClickListener(v->capture());

    LinearLayout search=new LinearLayout(this);
    search.setGravity(Gravity.CENTER_VERTICAL);
    search.setPadding(dp(10),dp(4),dp(10),dp(4));
    layout.addView(search,new LinearLayout.LayoutParams(-1,dp(65)));
    query=new EditText(this);query.setSingleLine(true);
    query.setHint("Име или шифра на производ");query.setTextSize(15);
    search.addView(query,new LinearLayout.LayoutParams(0,dp(52),1));
    Button find=button("Барај");
    search.addView(find,new LinearLayout.LayoutParams(-2,dp(52)));
    find.setOnClickListener(v->searchCode(query.getText().toString().trim()));

    hint=label("Производи од Setec.mk • достапност на официјалниот сајт",12,Color.DKGRAY);
    hint.setPadding(dp(12),0,dp(10),0);
    layout.addView(hint,new LinearLayout.LayoutParams(-1,dp(38)));
    progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    progress.setIndeterminate(true);progress.setVisibility(View.INVISIBLE);
    layout.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));

    web=new WebView(this);
    web.setBackgroundColor(Color.WHITE);
    layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    WebSettings settings=web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(new WebViewClient(){
      @Override public void onPageStarted(WebView view,String url,Bitmap icon){
        progress.setVisibility(View.VISIBLE);
      }
      @Override public void onPageFinished(WebView view,String url){
        progress.setVisibility(View.INVISIBLE);
        if(pending!=null&&trusted(url)){
          String code=pending;pending=null;
          handler.postDelayed(()->applySearch(code),750);
        }
      }
    });
    web.loadUrl(HOME);
  }

  private boolean trusted(String url){
    try{
      Uri u=Uri.parse(url);
      String host=u.getHost();
      return "https".equalsIgnoreCase(u.getScheme())&&
        ("setec.mk".equalsIgnoreCase(host)||"www.setec.mk".equalsIgnoreCase(host));
    }catch(Exception e){return false;}
  }
  private void searchCode(String value){
    String s=value==null?"":value.trim();
    if(s.isEmpty()){Toast.makeText(this,"Внеси име или шифра",Toast.LENGTH_SHORT).show();return;}
    query.setText(s);
    if(web==null)return;
    String current=web.getUrl();
    if(!trusted(current)||!("/".equals(Uri.parse(current).getPath()))){
      pending=s;
      web.loadUrl(HOME);
    }else applySearch(s);
  }
  private void applySearch(String value){
    if(web==null||!trusted(web.getUrl()))return;
    String q=JSONObject.quote(value);
    String js="(function(){"+
      "var i=[...document.querySelectorAll('input')].find(x=>/пребарај|search/i.test(x.placeholder||''));"+
      "if(!i)return false;"+
      "var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"+
      "setter.call(i,"+q+");"+
      "i.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:"+q+"}));"+
      "i.dispatchEvent(new Event('change',{bubbles:true}));"+
      "var f=i.closest('form');var b=f&&f.querySelector('button[type=submit]');"+
      "if(b)b.click();else if(f){try{f.requestSubmit()}catch(e){}}"+
      "else i.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',bubbles:true}));"+
      "return true;})()";
    web.evaluateJavascript(js,result->{
      if("true".equals(result))hint.setText("Пребарување: "+value+" — избери го производот.");
      else hint.setText("Сајтот не го врати полето за пребарување. Внеси ја шифрата на Setec.mk.");
    });
  }

  private void capture(){
    File folder=getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    if(folder==null){manual(null);return;}
    File out=new File(folder,"setec_"+System.currentTimeMillis()+".jpg");
    photoUri=FileProvider.getUriForFile(this,getPackageName()+".files",out);
    Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    camera.putExtra(MediaStore.EXTRA_OUTPUT,photoUri);
    camera.setClipData(ClipData.newUri(getContentResolver(),"label",photoUri));
    camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    try{startActivityForResult(camera,PHOTO);}
    catch(Exception e){Toast.makeText(this,"Нема достапна камера",Toast.LENGTH_LONG).show();manual(null);}
  }
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(req!=PHOTO||result!=RESULT_OK||photoUri==null)return;
    progress.setVisibility(View.VISIBLE);
    try{
      InputImage image=InputImage.fromFilePath(this,photoUri);
      recognizer.process(image)
        .addOnSuccessListener(t->{progress.setVisibility(View.INVISIBLE);manual(SkuExtractor.extract(t.getText()));})
        .addOnFailureListener(e->{progress.setVisibility(View.INVISIBLE);manual(null);});
    }catch(IOException e){progress.setVisibility(View.INVISIBLE);manual(null);}
  }
  private void manual(String detected){
    EditText entry=new EditText(this);
    entry.setSingleLine(true);entry.setInputType(InputType.TYPE_CLASS_NUMBER);
    entry.setHint("Шифра, пример 12959");entry.setText(detected==null?"":detected);
    LinearLayout wrap=new LinearLayout(this);
    wrap.setPadding(dp(20),0,dp(20),0);
    wrap.addView(entry,new LinearLayout.LayoutParams(-1,dp(56)));
    new AlertDialog.Builder(this)
      .setTitle(detected==null?"Внеси шифра":"Препознаена шифра")
      .setMessage("Провери го бројот од „Шифра:“ (не цената).")
      .setView(wrap)
      .setNegativeButton("Откажи",(d,w)->{})
      .setPositiveButton("Пребарај",(d,w)->{
        String s=entry.getText().toString().trim();
        if(!s.matches("[0-9]{4,8}")){
          Toast.makeText(this,"Невалидна шифра",Toast.LENGTH_LONG).show();return;
        }
        searchCode(s);
      }).show();
  }
  @Override public void onBackPressed(){
    if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();
  }
  @Override protected void onDestroy(){
    recognizer.close();
    if(web!=null){web.destroy();web=null;}
    super.onDestroy();
  }
}
