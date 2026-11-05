package mk.seteczaliha.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import org.json.JSONArray;
import org.json.JSONObject;
import android.text.Html;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Separate opt-in MODEL scanner. The existing auto-SKU scanner is untouched.
 * Camera frames are not OCR'ed automatically: only pressing SCAN captures one.
 * Research uses public search result snippets with clickable sources, never
 * invented specifications or a hidden paid API.
 */
public final class ModelScannerActivity extends ComponentActivity {
    private static final int ORANGE = Color.rgb(245, 130, 30);
    private static final int INK = Color.rgb(31, 33, 41);
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private ProcessCameraProvider cameraProvider;
    private ImageCapture capture;
    private Button scanButton, researchButton;
    private TextView status, internetStatus, facts, selectedLabel, setecStatus;
    private LinearLayout suggestions, sources, setecOptions;
    private EditText modelInput;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WebView setecBrowser;
    private String bestSetecUrl="",activeSetecModel="";
    private int setecStage=0; // 0 idle, 1 catalog search, 2 exact product
    private int setecSerial=0;
    private boolean setecMatched=false;
    private boolean homeSearchFallback=false,homeSearchSubmitted=false;
    private boolean scanning = false;
    private int researchSerial = 0;
    private ActivityResultLauncher<String> cameraPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF1C1C21);
        getWindow().setNavigationBarColor(Color.BLACK);

        cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> { if (granted) startCamera(); else setStatus("Камерата нема дозвола. Можеш рачно да внесеш модел."); }
        );
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFFF7F8FB);
        // Keep Setec's dynamic catalog in an independent full-width WebView.
        // This never navigates the app away from the model results screen.
        FrameLayout appRoot=new FrameLayout(this);
        setContentView(appRoot);
        setecBrowser=new WebView(this);
        setecBrowser.setAlpha(0.01f);
        setecBrowser.setClickable(false);
        WebSettings settings=setecBrowser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(false);
        settings.setBlockNetworkImage(true);
        appRoot.addView(setecBrowser,new FrameLayout.LayoutParams(-1,-1));
        appRoot.addView(page,new FrameLayout.LayoutParams(-1,-1));
        setecBrowser.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                final int serial=setecSerial;
                if(setecStage==1){
                    if(homeSearchFallback&&!homeSearchSubmitted&&(url.equals("https://www.setec.mk/")||url.equals("https://setec.mk/"))){
                        for(long delay:new long[]{450,1100})
                            handler.postDelayed(()->submitHomeSearch(serial),delay);
                    }
                    for(long delay:new long[]{650,1650,3050,5100})
                        handler.postDelayed(()->readSetecMatches(serial),delay);
                }else if(setecStage==2)for(long delay:new long[]{650,1350,2700})
                    handler.postDelayed(()->readSetecDetails(serial),delay);
            }
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12),dp(10),dp(12),dp(10));
        header.setBackgroundColor(0xFF1C1C21);
        page.addView(header);
        Button back = button("‹ Назад",0xFF3C3C43);
        header.addView(back,new LinearLayout.LayoutParams(dp(92),dp(44)));
        back.setOnClickListener(v -> finish());
        TextView title = label("SETEC · Скенирај модел",19,true,Color.WHITE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,-2,1);
        titleParams.leftMargin=dp(12);
        header.addView(title,titleParams);

        FrameLayout cameraPanel = new FrameLayout(this);
        page.addView(cameraPanel,new LinearLayout.LayoutParams(-1,dp(228)));
        PreviewView preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        cameraPanel.addView(preview,new FrameLayout.LayoutParams(-1,-1));

        View frame = new View(this);
        GradientDrawable border = new GradientDrawable();
        border.setColor(0x11000000);
        border.setCornerRadius(dp(13));
        border.setStroke(dp(3),ORANGE);
        frame.setBackground(border);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(-1,dp(115),Gravity.CENTER);
        frameParams.leftMargin=dp(22);frameParams.rightMargin=dp(22);
        cameraPanel.addView(frame,frameParams);

        LinearLayout captureRow = new LinearLayout(this);
        captureRow.setGravity(Gravity.CENTER_VERTICAL);
        captureRow.setPadding(dp(14),dp(8),dp(14),dp(8));
        captureRow.setBackgroundColor(0xFFF7F8FB);
        page.addView(captureRow);
        scanButton = button("▣ SCAN · Сними модел",ORANGE);
        captureRow.addView(scanButton,new LinearLayout.LayoutParams(-1,dp(53)));
        scanButton.setOnClickListener(v -> scanOnce());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(15),dp(6),dp(15),dp(36));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        status = label("Насочи го моделот во портокаловата рамка и притисни SCAN. Нема автоматско скенирање.",13,false,0xFF5B6270);
        content.addView(status);

        selectedLabel = label("Модел за пребарување",15,true,INK);
        topMargin(content,selectedLabel,dp(18));
        modelInput = new EditText(this);
        modelInput.setSingleLine(true);
        modelInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        modelInput.setTextSize(15);
        modelInput.setHint("пр. ASUS PRO B560M-C/CSM");
        modelInput.setSelectAllOnFocus(true);
        modelInput.setPadding(dp(12),dp(9),dp(12),dp(9));
        content.addView(modelInput,new LinearLayout.LayoutParams(-1,dp(52)));
        researchButton=button("⌕ Истражи го моделот",ORANGE);
        topMargin(content,researchButton,dp(8));
        researchButton.setOnClickListener(v -> research(modelInput.getText().toString()));

        TextView suggestionsTitle = label("Препознаени модели · допри за избор",13,true,INK);
        topMargin(content,suggestionsTitle,dp(18));
        suggestions=new LinearLayout(this);suggestions.setOrientation(LinearLayout.VERTICAL);
        content.addView(suggestions);
        suggestions.addView(label("По SCAN ќе се појават препознаените модели. Можеш и рачно да го поправиш текстот.",12,false,0xFF6A7280));

        setecStatus=label("Официјални податоци од Setec.mk ќе се појават тука.",13,true,0xFF835419);
        topMargin(content,setecStatus,dp(17));
        setecOptions=new LinearLayout(this);
        setecOptions.setOrientation(LinearLayout.VERTICAL);
        content.addView(setecOptions);
        facts=label("",13,false,INK);
        facts.setTextIsSelectable(true);
        topMargin(content,facts,dp(18));

        LinearLayout links=new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        topMargin(content,links,dp(11));
        Button web=button("Веб резултати",0xFF383F4A),setec=button("Setec.mk",0xFF383F4A);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1),b=new LinearLayout.LayoutParams(0,dp(48),1);
        b.leftMargin=dp(8);
        links.addView(web,a);links.addView(setec,b);
        web.setOnClickListener(v -> openSearch(modelInput.getText().toString(),""));
        setec.setOnClickListener(v -> openExactSetec());

        internetStatus = label("По SCAN автоматски ќе побарам јавно достапни извори и спецификации.",13,false,0xFF687181);
        topMargin(content,internetStatus,dp(14));
        sources=new LinearLayout(this);sources.setOrientation(LinearLayout.VERTICAL);
        content.addView(sources);

        activePreview=preview;
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera(preview);
        else cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private PreviewView activePreview;
    private void startCamera() { if(activePreview!=null)startCamera(activePreview); }
    private void startCamera(PreviewView view) {
        activePreview=view;
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if(isFinishing()||isDestroyed())return;
            try{
                cameraProvider=future.get();
                Preview live=new Preview.Builder().build();
                live.setSurfaceProvider(view.getSurfaceProvider());
                capture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,live,capture);
            }catch(Exception e){setStatus("Камерата не се отвори. Внеси модел рачно.");}
        },ContextCompat.getMainExecutor(this));
    }
    private void scanOnce(){
        if(scanning)return;
        if(capture==null){setStatus("Камерата уште не е подготвена. Можеш рачно да внесеш модел.");return;}
        scanning=true;scanButton.setEnabled(false);
        // Invalidate any previous search: never present an old model's data as new.
        researchSerial++;
        sources.removeAllViews();
        facts.setText("");
        internetStatus.setText("Се чита само текстот во портокаловата рамка.");
        setStatus("Снимив кадар. Препознавам текст само од рамката…");
        capture.takePicture(ContextCompat.getMainExecutor(this),new ImageCapture.OnImageCapturedCallback(){
            @Override public void onCaptureSuccess(ImageProxy proxy){
                try{
                    Bitmap bitmap=proxy.toBitmap();
                    Matrix matrix=new Matrix();
                    matrix.postRotate(proxy.getImageInfo().getRotationDegrees());
                    Bitmap upright=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                    proxy.close();
                    // CameraX captures a full image even though PreviewView only
                    // shows the central crop. OCR *only* the orange on-screen guide.
                    Bitmap modelArea=cropToScanGuide(upright);
                    InputImage input=InputImage.fromBitmap(modelArea,0);
                    recognizer.process(input)
                        .addOnSuccessListener(ModelScannerActivity.this::handleRecognized)
                        .addOnFailureListener(e->setStatus("Не можам да го прочитам моделот. Приближи или внеси рачно."))
                        .addOnCompleteListener(task->finishScan());
                }catch(Exception e){proxy.close();finishScan();setStatus("Неуспешно читање. Пробај повторно.");}
            }
            @Override public void onError(ImageCaptureException error){
                finishScan();setStatus("Не може да се слика. Пробај повторно.");
            }
        });
    }
    private void finishScan(){scanning=false;scanButton.setEnabled(true);}
    /**
     * PreviewView uses FILL_CENTER. Map the visible orange guide into the
     * captured upright bitmap; anything outside (prices, installment rates)
     * is deliberately not passed to text recognition.
     */
    private Bitmap cropToScanGuide(Bitmap upright) {
        int width=upright.getWidth(),height=upright.getHeight();
        int previewWidth=activePreview==null?0:activePreview.getWidth();
        int previewHeight=activePreview==null?0:activePreview.getHeight();
        if(previewWidth<40||previewHeight<40){
            // Conservative fallback if layout has not been measured.
            int h=Math.max(1,Math.round(height*0.23f));
            return Bitmap.createBitmap(upright,0,(height-h)/2,width,h);
        }
        float scale=Math.max(previewWidth/(float)width,previewHeight/(float)height);
        float guideWidth=Math.max(1,previewWidth-2f*dp(22));
        float guideHeight=Math.min(previewHeight,dp(115));
        // Tiny margin allows OCR to read characters on the visible orange border.
        int cropWidth=Math.min(width,Math.max(1,Math.round(guideWidth/scale*1.015f)));
        int cropHeight=Math.min(height,Math.max(1,Math.round(guideHeight/scale*1.015f)));
        int left=Math.max(0,(width-cropWidth)/2);
        int top=Math.max(0,(height-cropHeight)/2);
        return Bitmap.createBitmap(upright,left,top,cropWidth,cropHeight);
    }

    private void handleRecognized(Text imageText){
        List<String> lines=new ArrayList<>();
        for(Text.TextBlock block:imageText.getTextBlocks())
            for(Text.Line line:block.getLines())lines.add(line.getText());

        List<ModelNameParser.Candidate> options=ModelNameParser.rank(lines);
        suggestions.removeAllViews();
        if(options.isEmpty()){
            modelInput.setText("");
            facts.setText("");
            internetStatus.setText("Не е препознаен јасен модел. Не пребарувам за рати или цени.");
            suggestions.addView(label("Скенирај повторно со моделот во рамката или внеси го рачно.",12,false,0xFF966125));
            setStatus("Не прочитав сигурен модел. „12x“ и цените се игнорирани.");
            return;
        }
        for(ModelNameParser.Candidate candidate:options){
            final String name=candidate.model;
            Button choice=button("✓ "+name,0xFFE9EDF3);
            choice.setTextColor(INK);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
            params.topMargin=dp(5);
            params.height=dp(49);
            suggestions.addView(choice,params);
            choice.setOnClickListener(v->{
                modelInput.setText(name);
                modelInput.setSelection(name.length());
                research(name);
            });
        }
        ModelNameParser.Candidate best=options.get(0);
        modelInput.setText(best.model);
        modelInput.setSelection(best.model.length());
        if(ModelNameParser.likelyCorrect(best)){
            setStatus("Препознаен модел: "+best.model+". Ако е погрешен, избери друга понуда или поправи го.");
            research(best.model); // One-shot SCAN was explicitly pressed.
        }else{
            internetStatus.setText("Моделот не е доволно сигурен. Потврди го или поправи го пред пребарување.");
            setStatus("Можно име: "+best.model+". Провери го текстот и притисни „Истражи го моделот“.");
        }
    }
    private void research(String input){
        String model=input==null?"":input.replaceAll("[\\n\\r\\t]"," ").replaceAll("\\s+"," ").trim();
        if(model.length()<4){setStatus("Внеси го точниот модел (барем 4 знаци).");return;}
        if(model.length()>100)model=model.substring(0,100);
        modelInput.setText(model);
        modelInput.setSelection(model.length());
        final String query=model;
        final int serial=++researchSerial;
        facts.setText("ВАЖНО ЗА ПРОВЕРКА\n"+checklist(query)+"\n\nГи барам проверените спецификации од официјалниот производ.");
        internetStatus.setText("🔎 Дополнителни јавни извори за „"+query+"“…");
        sources.removeAllViews();
        startSetecLookup(query);
        networkExecutor.execute(() -> {
            List<Result> results=new ArrayList<>();
            String error="";
            try{results=fetchPublicResults(query);}catch(Exception e){error="Пребарувачот не врати автоматски податоци.";}
            final List<Result> received=results;
            final String message=error;
            runOnUiThread(() -> {
                if(serial!=researchSerial||isFinishing())return;
                renderSources(query,received,message);
            });
        });
    }
    private String checklist(String model){
        String m=model.toUpperCase(Locale.ROOT);
        if(m.matches(".*(?:B[45678]\\d{2}|Z[456789]\\d{2}|X[3578]\\d{2}|H[45678]\\d{2}|MOTHERBOARD|MAINBOARD|MATIČNA).*"))
            return "• Socket i kompatibilen procesor\n• DDR4/DDR5 i max RAM\n• M.2/SATA portovi\n• ATX/mATX/ITX format\n• BIOS i Wi‑Fi";
        if(m.matches(".*(?:RYZEN|INTEL|CORE\\s*I[3579]|PROCESSOR|CPU).*"))
            return "• Generacija i socket\n• Broj jadra/nitki\n• TDP/potrošuvačka\n• RAM podrška\n• Integrirana grafika";
        if(m.matches(".*(?:RTX|GTX|RADEON|RX\\s*\\d{4}|ARC\\s*[AB]\\d{3}).*"))
            return "• VRAM i tip\n• Potrošuvačka i potrebno PSU\n• Dolžina na kartičkata\n• Video priklučoci\n• Performansi i driveri";
        if(m.matches(".*(?:SSD|NVME|HDD|SNV|MZ-|SATA).*"))
            return "• Kapacitet\n• NVMe ili SATA\n• M.2/2.5-inch format\n• Čitanje i zapišuvanje\n• Garancija i trajnost";
        if(m.matches(".*(?:DDR[345]|DIMM|RAM|FURY|VENGEANCE).*"))
            return "• DDR tip i kapacitet\n• DIMM ili SO-DIMM\n• MHz/MT/s i latencija\n• Broj moduli\n• Kompatibilnost so matična";
        return "• Točen model i proizveduvač\n• Najvažni tehnički specifikacii\n• Kompatibilnost i konektori\n• Garancija i dokumentacija\n• Cena i dostapnost vo Setec";
    }

    // One search per chosen model. Research stays inside this Activity.
    private void startSetecLookup(String requestedModel){
        bestSetecUrl="";
        setecOptions.removeAllViews();
        activeSetecModel=ModelNameParser.normalizeModel(requestedModel);
        int serial=++setecSerial;
        setecStage=1;setecMatched=false;homeSearchFallback=false;homeSearchSubmitted=false;
        setecStatus.setText("🔎 Пребарувам производи на Setec.mk…");
        String term=SetecModelMatcher.searchTerm(activeSetecModel);
        if(term.isEmpty()){setecStatus.setText("Внеси модел за пребарување на Setec.");return;}
        Uri target=Uri.parse("https://www.setec.mk/search-result").buildUpon()
            .appendQueryParameter("q",term).build();
        setecBrowser.stopLoading();
        setecBrowser.loadUrl(target.toString());
        handler.postDelayed(()->{
            if(serial==setecSerial&&setecStage==1&&!setecMatched){
                homeSearchFallback=true;
                setecStatus.setText("Пробувам и преку официјалното пребарување на Setec…");
                setecBrowser.loadUrl("https://www.setec.mk/");
            }
        },6300);
        handler.postDelayed(()->{
            if(serial==setecSerial&&setecStage==1&&!setecMatched){
                setecStatus.setText("Нема сигурно совпаѓање со точниот модел. Провери го текстот или избери понуден Setec производ.");
            }
        },16000);
    }

    private void submitHomeSearch(int serial){
        if(serial!=setecSerial||setecStage!=1||homeSearchSubmitted)return;
        String q=JSONObject.quote(SetecModelMatcher.searchTerm(activeSetecModel));
        String js="(function(){"+
            "let input=[...document.querySelectorAll('input')].find(x=>/пребарај|search/i.test(x.placeholder||'')||x.type==='search');"+
            "if(!input)return 'missing';"+
            "let setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(input,"+q+");"+
            "input.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:"+q+"}));"+
            "input.dispatchEvent(new Event('change',{bubbles:true}));"+
            "let form=input.closest('form');"+
            "if(form){try{form.requestSubmit()}catch(e){}}else{let b=input.parentElement&&input.parentElement.querySelector('button');if(b)b.click();else input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));}"+
            "return 'submitted';})()";
        setecBrowser.evaluateJavascript(js,callback->{
            if(serial!=setecSerial||setecStage!=1)return;
            if("submitted".equals(decodeJs(callback))){
                homeSearchSubmitted=true;
                for(long delay:new long[]{900,2000,3800,5600,8200})
                    handler.postDelayed(()->readSetecMatches(serial),delay);
            }
        });
    }

    private static String decodeJs(String value){
        if(value==null||"null".equals(value)||"undefined".equals(value))return null;
        try{
            JSONArray arr=new JSONArray("["+value+"]");
            return arr.optString(0,null);
        }catch(Exception ignored){return null;}
    }

    private void readSetecMatches(int serial){
        if(serial!=setecSerial||setecStage!=1||setecMatched||setecBrowser==null)return;
        String js="(function(){\n try{\n  let result=new Map();\n  for(const a of document.querySelectorAll('a[href*=\"/products/\"]')){\n   let u;try{u=new URL(a.getAttribute('href'),location.href)}catch(e){continue}\n   if(!['www.setec.mk','setec.mk'].includes(u.hostname)||result.has(u.href))continue;\n   let card=a.closest('article,li,[class*=\"product\"],[class*=\"Product\"],[class*=\"card\"],[class*=\"Card\"]')||a.parentElement||a;\n   let heading=card.querySelector('h2,h3,h4,[class*=\"title\"],[class*=\"name\"]');\n   let name=(heading?.innerText||a.title||a.innerText||'').replace(/\\s+/g,' ').trim();\n   if(name.length<5||name.length>210)continue;\n   result.set(u.href,{url:u.href,name:name});\n   if(result.size>=100)break;\n  }\n  return JSON.stringify([...result.values()]);\n }catch(e){return '[]'}\n})()";
        setecBrowser.evaluateJavascript(js,encoded->{
            if(serial!=setecSerial||setecStage!=1||setecMatched)return;
            try{
                String raw=decodeJs(encoded);
                if(raw==null)return;
                JSONArray options=new JSONArray(raw);
                java.util.ArrayList<JSONObject> matches=new java.util.ArrayList<>();
                for(int i=0;i<options.length();i++){
                    JSONObject item=options.optJSONObject(i);
                    if(item==null)continue;
                    String url=item.optString("url"),name=item.optString("name");
                    if(!isSetecProduct(url))continue;
                    int score=SetecModelMatcher.score(activeSetecModel,name);
                    if(score>=85){
                        item.put("matchScore",score);
                        matches.add(item);
                    }
                }
                matches.sort((a,b)->Integer.compare(b.optInt("matchScore"),a.optInt("matchScore")));
                if(matches.isEmpty())return;
                JSONObject top=matches.get(0);
                int best=top.optInt("matchScore");
                int second=matches.size()>1?matches.get(1).optInt("matchScore"):-1;
                // Never silently pick the wrong capacity/color variant.
                if(best>=100 && best-second>=4){
                    selectSetecProduct(top.optString("url"),top.optString("name"));
                }else{
                    setecOptions.removeAllViews();
                    setecStatus.setText("Пронајдов слични производи. Избери го точниот модел/боја за да ги видиш спецификациите:");
                    for(int i=0;i<Math.min(5,matches.size());i++){
                        JSONObject match=matches.get(i);
                        final String name=match.optString("name"),url=match.optString("url");
                        Button choice=button("✓ "+name,0xFFE9EDF3);
                        choice.setTextColor(INK);
                        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                        lp.topMargin=dp(5);
                        setecOptions.addView(choice,lp);
                        choice.setOnClickListener(v->selectSetecProduct(url,name));
                    }
                }
            }catch(Exception ignored){}
        });
    }

    private void selectSetecProduct(String url,String name){
        if(!isSetecProduct(url)||setecStage!=1)return;
        setecMatched=true;setecStage=2;bestSetecUrl=url;
        setecOptions.removeAllViews();
        setecStatus.setText("✓ Точен производ: "+name+"\nГи читам спецификациите на Setec.mk…");
        setecBrowser.loadUrl(url);
    }

    private static boolean isSetecProduct(String link){
        try{
            Uri u=Uri.parse(link);
            return "https".equalsIgnoreCase(u.getScheme())
                &&("setec.mk".equalsIgnoreCase(u.getHost())||"www.setec.mk".equalsIgnoreCase(u.getHost()))
                &&u.getPath()!=null&&u.getPath().startsWith("/products/");
        }catch(Exception e){return false;}
    }

    private void readSetecDetails(int serial){
        if(serial!=setecSerial||setecStage!=2||setecBrowser==null)return;
        String js="(function(){\n try{\n  const body=document.body?.innerText||'';\n  const title=(document.querySelector('h1')?.innerText||'').replace(/\\s+/g,' ').trim();\n  // Setec renders technical specifications under an accordion/tab.\n  const button=[...document.querySelectorAll('button,[role=\"tab\"],a')].find(x=>\n   /Технички\\s*спецификации/i.test((x.innerText||x.textContent||'').trim())&&\n   (x.innerText||x.textContent||'').length<80);\n  try{if(button&&!window.__modelSpecOpened){button.click();window.__modelSpecOpened=true}}catch(e){}\n  const findValue=label=>{\n   const m=body.match(new RegExp(label+'\\\\s*:?\\\\s*([\\\\d.,]+)\\\\s*ден','i'));\n   return m?m[1]:'';\n  };\n  const sku=(body.match(/Шифра\\s*:?\\s*(\\d{4,8})/i)||[])[1]||'';\n  const warranty=(body.match(/Гаранција\\s*:?\\s*([^\\n]{2,70})/i)||[])[1]||'';\n  let specs='';\n  // Prefer the visible technical section over unrelated page navigation text.\n  const candidates=body.split(/Технички\\s*спецификации/i);\n  if(candidates.length>1){\n   const tail=candidates[candidates.length-1].trim();\n   const stop=tail.search(/(?:\\n\\s*(?:Оцени|Рецензии|Поврзани\\s*производи|Достава|Услови|Препорачани|Слични\\s*производи)\\s*\\n)/i);\n   const data=(stop>0?tail.slice(0,stop):tail).slice(0,1600);\n   let lines=data.split('\\n').map(v=>v.trim()).filter(x=>x.length>2&&x.length<180&&\n      !/^(?:Мени|Пребарај|Клуб\\s*цена|Редовна\\s*цена|Преглед|Cart|Compare)/i.test(x));\n   if(lines.length>1)specs=lines.slice(0,20).join('\\n');\n  }\n  return JSON.stringify({name:title,sku,club:findValue('Клуб цена'),\n   regular:findValue('Редовна цена'),warranty,specs});\n }catch(e){return '{}'}\n})()";
        setecBrowser.evaluateJavascript(js,encoded->{
            if(serial!=setecSerial||setecStage!=2)return;
            try{
                String raw=decodeJs(encoded);
                if(raw==null)return;
                JSONObject product=new JSONObject(raw);
                String name=product.optString("name");
                if(SetecModelMatcher.score(activeSetecModel,name)<100)return;
                String code=product.optString("sku");
                String club=product.optString("club");
                String regular=product.optString("regular");
                String warranty=product.optString("warranty");
                String specs=product.optString("specs").trim();
                StringBuilder lines=new StringBuilder();
                lines.append("ПРОИЗВОД ОД SETEC.MK\n").append(name);
                if(!code.isEmpty())lines.append("\nSetec шифра: ").append(code);
                if(!club.isEmpty())lines.append("\nКлуб цена: ").append(club).append(" ден.");
                if(!regular.isEmpty())lines.append("\nРедовна цена: ").append(regular).append(" ден.");
                if(!warranty.isEmpty())lines.append("\nГаранција: ").append(warranty);
                if(!specs.isEmpty()){
                    lines.append("\n\nТЕХНИЧКИ СПЕЦИФИКАЦИИ\n").append(specs);
                    setecStage=3;
                    setecStatus.setText("✓ Најдени технички податоци на Setec.mk. Изворот може да се отвори со копчето Setec.mk.");
                }else{
                    setecStatus.setText("✓ Пронајден производот, но сеуште ги читам спецификациите од Setec.mk.");
                }
                lines.append("\n\nВАЖНО ЗА ПРОВЕРКА\n").append(checklist(activeSetecModel))
                    .append("\n\nЗа компатибилност и ревизии секогаш провери и кај производителот.");
                facts.setText(lines.toString());
            }catch(Exception ignored){}
        });
        handler.postDelayed(()->{
            if(serial==setecSerial&&setecStage==2)
                setecStatus.setText("✓ Пронајден точен производ на Setec. Specifikaciite možda ne se čitаat avtomatski; vidi go oficijalniot proizvod.");
        },6000);
    }

    private void openExactSetec(){
        String model=modelInput.getText().toString().trim();
        if(model.length()<3){setStatus("Прво внеси или скенирај модел.");return;}
        if(!bestSetecUrl.isEmpty()&&isSetecProduct(bestSetecUrl)){openUri(bestSetecUrl);return;}
        Uri page=Uri.parse("https://www.setec.mk/search-result").buildUpon()
            .appendQueryParameter("q",SetecModelMatcher.searchTerm(model)).build();
        openUri(page.toString());
    }

    private static final class Result{
        String title,description,url;
        Result(String title,String description,String url){this.title=title;this.description=description;this.url=url;}
    }
    private List<Result> fetchPublicResults(String model) throws Exception{
        String q="\""+model+"\" specifications";
        URL url=new URL("https://www.bing.com/search?format=rss&q="+Uri.encode(q));
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        connection.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        connection.setRequestProperty("Accept","application/rss+xml,application/xml,text/xml,*/*");
        connection.setInstanceFollowRedirects(true);
        try{
            if(connection.getResponseCode()!=200)throw new Exception("HTTP status "+connection.getResponseCode());
            XmlPullParser parser=Xml.newPullParser();
            parser.setInput(new BufferedInputStream(connection.getInputStream()),"UTF-8");
            List<Result> out=new ArrayList<>();
            String title="",link="",description="",tag=null;
            boolean inItem=false;
            int event;
            while((event=parser.next())!=XmlPullParser.END_DOCUMENT&&out.size()<8){
                if(event==XmlPullParser.START_TAG){
                    String name=parser.getName();
                    if("item".equalsIgnoreCase(name)){inItem=true;title="";description="";link="";}
                    else if(inItem&&("title".equals(name)||"description".equals(name)||"link".equals(name)))tag=name;
                }else if(event==XmlPullParser.TEXT&&inItem&&tag!=null){
                    String value=parser.getText();
                    if("title".equals(tag))title+=value;
                    else if("link".equals(tag))link+=value;
                    else if("description".equals(tag))description+=value;
                }else if(event==XmlPullParser.END_TAG){
                    String name=parser.getName();
                    if(name.equals(tag))tag=null;
                    if("item".equalsIgnoreCase(name)){
                        inItem=false;
                        String safe=checkResultUrl(link);
                        if(!safe.isEmpty()&&!title.trim().isEmpty()){
                            String descr=Html.fromHtml(description,Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+"," ").trim();
                            out.add(new Result(title.trim(),descr.length()>430?descr.substring(0,430)+"…":descr,safe));
                        }
                    }
                }
            }
            return out;
        }finally{connection.disconnect();}
    }
    private static String checkResultUrl(String link){
        try{
            Uri u=Uri.parse(link.trim());
            String host=u.getHost();
            if("https".equalsIgnoreCase(u.getScheme())&&host!=null&&!host.isEmpty())return u.toString();
        }catch(Exception ignored){}
        return "";
    }
    private void renderSources(String model,List<Result> found,String error){
        sources.removeAllViews();
        if(found.isEmpty()){
            internetStatus.setText("Нема автоматски прочитани izvori. Otvori „Veb rezultati“ ili „Setec.mk“ za direktno prebaruvanje.");
            if(!error.isEmpty())sources.addView(label(error,12,false,0xFF915F28));
            return;
        }
        internetStatus.setText("🔎 Najdeni "+found.size()+" javni izvori za „"+model+"“. Dopri rezultat za da gi proveriš celosnite specifikacii.");
        for(Result source:found){
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(12),dp(12),dp(12));
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(Color.WHITE);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),0xFFE1E4EA);
            card.setBackground(bg);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(9);
            sources.addView(card,lp);
            TextView title=label(source.title,14,true,INK);
            card.addView(title);
            if(!source.description.isEmpty()){
                TextView desc=label(source.description,12,false,0xFF5A6576);
                desc.setPadding(0,dp(6),0,0);
                card.addView(desc);
            }
            Uri uri=Uri.parse(source.url);
            String host=uri.getHost()==null?"Izvor":uri.getHost();
            TextView link=label(host+"  ↗",11,true,0xFFE87919);
            link.setPadding(0,dp(7),0,0);
            card.addView(link);
            card.setOnClickListener(v->openUri(source.url));
        }
        sources.addView(label("Rezultatite se kratki izvodi od Internet. Za točni vrednosti (socket, DDR, TDP, kapacitet) proveri kaj proizveduvačot.",12,false,0xFF6A7180));
    }
    private void openSearch(String raw,String prefix){
        String value=raw==null?"":raw.trim();
        if(value.length()<3){setStatus("Prvo skeniraj ili vnesi model.");return;}
        String query=prefix+"\""+value+"\" specifications";
        openUri("https://www.google.com/search?q="+Uri.encode(query));
    }
    private void openUri(String value){
        try{Uri uri=Uri.parse(value);if(!"https".equalsIgnoreCase(uri.getScheme()))return;startActivity(new Intent(Intent.ACTION_VIEW,uri));}
        catch(Exception e){setStatus("Ne e dostapen pregledačot. Proveri internet konekcija.");}
    }
    private void setStatus(String message){if(status!=null)status.setText(message);}
    private void topMargin(LinearLayout parent,View view,int margin){
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=margin;parent.addView(view,params);
    }
    private Button button(String text,int background){
        Button b=new Button(this);
        b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(background));
        return b;
    }
    private TextView label(String message,int size,boolean bold,int color){
        TextView v=new TextView(this);v.setText(message);v.setTextSize(size);
        v.setTextColor(color);v.setLineSpacing(dp(2),1.0f);
        if(bold)v.setTypeface(null,Typeface.BOLD);
        return v;
    }
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    @Override protected void onDestroy(){
        researchSerial++;
        if(cameraProvider!=null)cameraProvider.unbindAll();
        recognizer.close();
        cameraExecutor.shutdownNow();networkExecutor.shutdownNow();
        super.onDestroy();
    }
}
