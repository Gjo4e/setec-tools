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
    private Button scanButton, researchButton, compareButton;
    private boolean comparePicker=false;
    private int compareSlot=1;
    private String compareSkuRequested="";
    private final List<String> compareSkuCandidateUrls=new ArrayList<>();
    private int compareSkuCandidateIndex=0;
    private boolean compareSkuSearchFallback=false;
    private String compareModel="",compareSku="",compareClub="",compareRegular="";
    private List<GoogleModelResearch.Fact> compareFacts=new ArrayList<>();
    private boolean compareResearchBusy=false;
    private TextView status, internetStatus, facts, selectedLabel, setecStatus;
    private LinearLayout suggestions, sources, setecOptions;
    private EditText modelInput;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WebView setecBrowser, googleBrowser;
    private TextView googleStatus, googleFacts;
    private LinearLayout googleSources;
    private boolean googleHasResults=false;
    private int bestFactCount=0;
    private String bestSetecUrl="",activeSetecModel="";
    private int setecStage=0; // 0 idle, 1 catalog search, 2 exact product
    private int setecSerial=0;
    private boolean setecMatched=false;
    private boolean homeSearchFallback=false,homeSearchSubmitted=false;
    private java.util.List<String> setecQueries=new java.util.ArrayList<>();
    private int setecQueryIndex=0,lookupGeneration=0,slugHostIndex=0;
    private String officialSlug="";
    private int setecSpecAttempts=0;
    private boolean scanning = false;
    private int researchSerial = 0;
    private ActivityResultLauncher<String> cameraPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        comparePicker=getIntent().getBooleanExtra("compare_pick",false);
        compareSlot=getIntent().getIntExtra("compare_slot",1);
        compareSkuRequested=getIntent().getStringExtra("compare_sku");
        if(compareSkuRequested==null||!compareSkuRequested.matches("[0-9]{4,8}"))compareSkuRequested="";
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
                final int serial=setecSerial,generation=lookupGeneration;
                if(setecStage==5){
                    for(long delay:new long[]{450,1150,2100})
                        handler.postDelayed(()->submitCompareSkuSearch(serial,generation),delay);
                }else if(setecStage==6){
                    for(long delay:new long[]{550,1600,3100,4600,6200})
                        handler.postDelayed(()->readCompareSkuMatches(serial,generation),delay);
                }else if(setecStage==7){
                    for(long delay:new long[]{650,1700,2900})
                        handler.postDelayed(()->readCompareSkuProduct(serial,generation),delay);
                }else if(setecStage==4){
                    for(long delay:new long[]{450,1250,2500,3500})
                        handler.postDelayed(()->readDirectSetecModel(serial,generation),delay);
                }else if(setecStage==1){
                    if(homeSearchFallback&&!homeSearchSubmitted&&
                        (url.equals("https://www.setec.mk/")||url.equals("https://setec.mk/"))){
                        for(long delay:new long[]{500,1300})
                            handler.postDelayed(()->submitHomeSearch(serial),delay);
                    }
                    for(long delay:new long[]{550,1400,2450,3650})
                        handler.postDelayed(()->{
                            if(generation==lookupGeneration)readSetecMatches(serial);
                        },delay);
                }else if(setecStage==2){
                    for(long delay:new long[]{650,1550,2900,4500})
                        handler.postDelayed(()->readSetecDetails(serial),delay);
                }
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
        TextView title = label(comparePicker?"SETEC · Шифра за споредба":"SETEC · Скенирај модел",19,true,Color.WHITE);
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
        if(comparePicker){
            cameraPanel.setVisibility(View.GONE);
            scanButton.setVisibility(View.GONE);
        }
        compareButton=button(comparePicker
            ?"✓ Зачувај модел "+compareSlot+" за споредба"
            :"⚖ СПОРЕДИ ДВА МОДЕЛИ",0xFF36475B);
        LinearLayout.LayoutParams compareParams=new LinearLayout.LayoutParams(-1,dp(48));
        compareParams.topMargin=dp(6);
        captureRow.setOrientation(LinearLayout.VERTICAL);
        captureRow.addView(compareButton,compareParams);
        compareButton.setOnClickListener(v->{
            if(comparePicker)saveForComparison();
            else startActivity(new Intent(this,CompareModelsActivity.class));
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(15),dp(6),dp(15),dp(36));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        status = label(comparePicker?"Ја проверувам SETEC шифрата и ги читам клучните податоци само од Setec.mk.":"Сликај го моделот или целата цена. Притисни SCAN — податоците се читаат само од Setec.mk.",13,false,0xFF5B6270);
        content.addView(status);

        selectedLabel = label(comparePicker?"SETEC шифра / пронајден модел":"Модел за пребарување",15,true,INK);
        topMargin(content,selectedLabel,dp(18));
        modelInput = new EditText(this);
        modelInput.setSingleLine(true);
        modelInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        modelInput.setTextSize(15);
        modelInput.setHint("пр. ASUS PRO B560M-C/CSM");
        modelInput.setSelectAllOnFocus(true);
        modelInput.setPadding(dp(12),dp(9),dp(12),dp(9));
        content.addView(modelInput,new LinearLayout.LayoutParams(-1,dp(52)));
        researchButton=button(comparePicker?"⌕ Провери SETEC шифра":"⌕ Истражи го моделот",ORANGE);
        topMargin(content,researchButton,dp(8));
        researchButton.setOnClickListener(v -> {
            if(comparePicker)startCompareSkuLookup(compareSkuRequested);
            else research(modelInput.getText().toString());
        });

        TextView suggestionsTitle = label("Препознаени модели · допри за избор",13,true,INK);
        topMargin(content,suggestionsTitle,dp(18));
        suggestions=new LinearLayout(this);suggestions.setOrientation(LinearLayout.VERTICAL);
        content.addView(suggestions);
        suggestions.addView(label("По SCAN ќе се појават препознаените модели. Можеш и рачно да го поправиш текстот.",12,false,0xFF6A7280));

        setecStatus=label("SETEC.MK · Модел, шифра, цена и кратки спецификации.",13,true,0xFF835419);
        topMargin(content,setecStatus,dp(17));
        setecOptions=new LinearLayout(this);
        setecOptions.setOrientation(LinearLayout.VERTICAL);
        content.addView(setecOptions);
        facts=label("",14,false,INK);
        facts.setTextIsSelectable(true);
        topMargin(content,facts,dp(8));
        googleStatus=label("НАЈВАЖНИ СПЕЦИФИКАЦИИ · само од Setec.mk",14,true,0xFF87551F);
        topMargin(content,googleStatus,dp(20));
        googleFacts=label("",14,false,INK);
        googleFacts.setTextIsSelectable(true);
        topMargin(content,googleFacts,dp(6));
        googleSources=new LinearLayout(this);
        googleSources.setOrientation(LinearLayout.VERTICAL);
        content.addView(googleSources);

        LinearLayout links=new LinearLayout(this);
        links.setOrientation(LinearLayout.VERTICAL);
        topMargin(content,links,dp(11));
        Button setec=button("Отвори го производот на Setec.mk",0xFF383F4A);
        links.addView(setec,new LinearLayout.LayoutParams(-1,dp(48)));
        setec.setOnClickListener(v -> openExactSetec());

        internetStatus=label("По пребарување ќе се прикажат само важни податоци од точниот Setec производ.",13,false,0xFF687181);
        topMargin(content,internetStatus,dp(14));
        sources=new LinearLayout(this);
        sources.setOrientation(LinearLayout.VERTICAL);
        content.addView(sources);

        activePreview=preview;
        if(comparePicker){
            modelInput.setText(compareSkuRequested);
            modelInput.setEnabled(false);
            suggestions.removeAllViews();
            suggestions.addView(label("Во споредба се чита само бројот до „Шифра:“. Нема читање букви од етикетата.",13,false,0xFF55606E));
            handler.postDelayed(()->startCompareSkuLookup(compareSkuRequested),320);
        }else if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera(preview);
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
        clearComparisonData("");
        // Invalidate any previous search: never present an old model's data as new.
        researchSerial++;
        sources.removeAllViews();
        facts.setText("");
        internetStatus.setText("Читам цела етикета, а ги игнорирам цените, ратите и шифрите.");
        setStatus("Снимив кадар. Го барам моделот на целата слика…");
        capture.takePicture(ContextCompat.getMainExecutor(this),new ImageCapture.OnImageCapturedCallback(){
            @Override public void onCaptureSuccess(ImageProxy proxy){
                try{
                    Bitmap bitmap=proxy.toBitmap();
                    Matrix matrix=new Matrix();
                    matrix.postRotate(proxy.getImageInfo().getRotationDegrees());
                    Bitmap upright=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                    proxy.close();
                    // The whole price tag can be photographed. OCR the full frame;
                    // model selection is based on product-code syntax and OCR geometry,
                    // NOT on the first text, the largest price, or the orange guide.
                    InputImage input=InputImage.fromBitmap(upright,0);
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
        List<SmartModelSelector.Line> observed=new ArrayList<>();
        for(Text.TextBlock block:imageText.getTextBlocks()){
            for(Text.Line line:block.getLines()){
                android.graphics.Rect rect=line.getBoundingBox();
                observed.add(new SmartModelSelector.Line(
                    line.getText(),rect==null?0:rect.top,rect==null?0:rect.height()));
            }
        }
        List<ModelNameParser.Candidate> options=SmartModelSelector.rank(observed);
        suggestions.removeAllViews();
        if(options.isEmpty()){
            modelInput.setText("");
            facts.setText("");
            internetStatus.setText("Не е препознаен јасен модел. Не пребарувам за рати или цени.");
            suggestions.addView(label("Нема сигурен модел на сликата. Сликај ја целата етикета почитливо или внеси ја ознаката рачно.",12,false,0xFF966125));
            setStatus("Не прочитав сигурен модел. Цените, ратите, шифрите и гаранцијата ги игнорирам.");
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
        // When a photo contains two neighboring price tags, do not silently
        // choose the wrong product. Show both model codes for manual selection.
        boolean ambiguous=options.size()>1&&best.score-options.get(1).score<9;
        if(ModelNameParser.likelyCorrect(best)&&!ambiguous){
            setStatus("Препознаен модел: "+best.model+". Ако е погрешен, избери друга понуда или поправи го.");
            research(best.model); // One-shot SCAN was explicitly pressed.
        }else if(ambiguous){
            internetStatus.setText("На сликата има повеќе модели. Избери го точниот подолу; нема да истражувам погрешен модел.");
            setStatus("Препознаени се повеќе модели на целата етикета. Допри го точниот.");
        }else{
            internetStatus.setText("Моделот не е доволно сигурен. Потврди го или поправи го пред пребарување.");
            setStatus("Можно име: "+best.model+". Провери го текстот и притисни „Истражи го моделот“.");
        }
    }
    private void clearComparisonData(String model){
        compareModel=model;
        compareResearchBusy=!model.isEmpty();
        compareSku="";compareClub="";compareRegular="";
        compareFacts=new ArrayList<>();
    }

    private void saveForComparison(){
        String displayed=modelInput.getText().toString().trim();
        if(displayed.length()<4){
            setStatus("Прво скенирај или внеси точен модел.");
            return;
        }
        if(!displayed.equals(compareModel)){
            if(comparePicker){
                setStatus("Шифрата сѐ уште не е поврзана со сигурен SETEC модел. Пробај повторно со друга шифра.");
                return;
            }
            research(displayed);
            setStatus("Прво ги проверувам податоците за моделот. Потоа притисни „Зачувај“.");
            return;
        }
        if(comparePicker&&!compareSkuRequested.equals(compareSku)){
            setStatus("Не е потврдена точната SETEC шифра. Не можам да го зачувам производот.");
            return;
        }
        if(compareResearchBusy){
            setStatus("Сѐ уште ги читам спецификациите од Setec.mk. Пробај „Зачувај“ повторно.");
            return;
        }
        try{
            JSONObject record=new JSONObject();
            record.put("name",compareModel);
            record.put("sku",compareSku);
            record.put("club",compareClub);
            record.put("regular",compareRegular);
            JSONArray arr=new JSONArray();
            for(GoogleModelResearch.Fact fact:compareFacts){
                if(fact==null||!SetecPageData.isOfficialProductUrl(fact.url))continue;
                JSONObject data=new JSONObject();
                data.put("label",fact.label);
                data.put("value",fact.value);
                data.put("url",fact.url);
                arr.put(data);
            }
            record.put("facts",arr);
            Intent result=new Intent();
            result.putExtra("compare_result",record.toString());
            setResult(RESULT_OK,result);
            finish();
        }catch(Exception error){
            setStatus("Не можам да го зачувам моделот. Пробај повторно.");
        }
    }

    private void research(String input){research(input,true);}
    private void research(String input,boolean lookUpModelOnSetec){
        String model=input==null?"":input.replaceAll("[\\n\\r\\t]"," ").replaceAll("\\s+"," ").trim();
        if(model.length()<4){setStatus("Внеси точен модел (барем 4 знаци).");return;}
        if(model.length()>110)model=model.substring(0,110);
        modelInput.setText(model);
        modelInput.setSelection(model.length());
        ++researchSerial;
        if(lookUpModelOnSetec){
            clearComparisonData(model);
            facts.setText("");
            compareResearchBusy=false;
            googleFacts.setText("");
            googleSources.removeAllViews();
            googleStatus.setText("НАЈВАЖНИ СПЕЦИФИКАЦИИ · само од Setec.mk");
            internetStatus.setText("Барам точен модел на Setec.mk. Нема Google пребарување.");
            startSetecLookup(model);
        }else{
            // A verified SKU is already shown; no external research and no reset.
            compareModel=model;
            internetStatus.setText("Точниот производ е потврден на Setec.mk.");
        }
    }

    private void startGoogleResearch(String model){
        googleHasResults=false;
        bestFactCount=0;
        googleFacts.setText("");
        googleSources.removeAllViews();
        googleStatus.setText("🔎 Google: барам податоци за точниот модел и независни извори…");
        if(googleBrowser==null)return;
        final int serial=researchSerial;
        String query=GoogleModelResearch.searchQuery(model);
        Uri url=Uri.parse("https://www.google.com/search").buildUpon()
            .appendQueryParameter("q",query)
            .appendQueryParameter("hl","en").appendQueryParameter("num","10").build();
        googleBrowser.stopLoading();
        googleBrowser.loadUrl(url.toString());
        handler.postDelayed(()->{
            if(serial==researchSerial&&!googleHasResults&&!isFinishing()){
                googleStatus.setText("Google не врати автоматски читливи резултати. Ќе користам други јавни извори ако се достапни; копчето „Веб резултати“ останува за рачна проверка.");
            }
        },10500);
    }

    private void readGoogleResults(int serial){
        if(serial!=researchSerial||googleHasResults||googleBrowser==null||isFinishing())return;
        String script="(function(){\n try{\n   const found=[],seen=new Set();\n   for(const h of document.querySelectorAll('h3')){\n     const a=h.closest('a[href]');\n     if(!a)continue;\n     let u;try{u=new URL(a.getAttribute('href'),location.href)}catch(e){continue}\n     if(u.protocol!=='https:'||u.hostname.endsWith('google.com')||u.hostname.endsWith('googleusercontent.com')||seen.has(u.href))continue;\n     const title=(h.innerText||h.textContent||'').replace(/\\s+/g,' ').trim();\n     if(title.length<5||title.length>220)continue;\n     const container=h.closest('div.MjjYud,div.g,article,div[data-hveid]')||h.parentElement?.parentElement?.parentElement;\n     let snippet=container?.querySelector('.VwiC3b,.IsZvec,.yXK7lf,[data-sncf]')?.innerText||'';\n     if(snippet.length<15){\n       let all=(container?.innerText||'').replace(/\\s+/g,' ').trim();\n       snippet=all.startsWith(title)?all.substring(title.length).trim():all;\n     }\n     snippet=snippet.replace(/\\s+/g,' ').replace(/^[-–·|:, ]+/,'').trim().slice(0,550);\n     seen.add(u.href);\n     found.push({title,snippet,url:u.href});\n     if(found.length>=9)break;\n   }\n   return JSON.stringify(found);\n }catch(e){return '[]';}\n})()";
        googleBrowser.evaluateJavascript(script,encoded->{
            if(serial!=researchSerial||googleHasResults||isFinishing())return;
            try{
                String value=decodeJs(encoded);
                if(value==null)return;
                JSONArray items=new JSONArray(value);
                String model=modelInput.getText().toString().trim();
                List<GoogleModelResearch.Result> list=new ArrayList<>();
                java.util.HashSet<String> urls=new java.util.HashSet<>();
                for(int i=0;i<items.length();i++){
                    JSONObject item=items.optJSONObject(i);
                    if(item==null)continue;
                    String url=checkResultUrl(item.optString("url"));
                    if(url.isEmpty()||!urls.add(url))continue;
                    GoogleModelResearch.Result hit=new GoogleModelResearch.Result(
                        item.optString("title"),item.optString("snippet"),url);
                    if(GoogleModelResearch.relevant(model,hit))list.add(hit);
                    if(list.size()>=7)break;
                }
                if(GoogleModelResearch.extract(model,list).size()>=3){
                    googleHasResults=true;
                    showGoogleSources(model,list,"Google");
                }
            }catch(Exception ignored){}
        });
    }

    private void showGoogleSources(String model,List<GoogleModelResearch.Result> found,String provider){
        googleSources.removeAllViews();
        if(found.isEmpty())return;
        googleStatus.setText("✓ "+provider+": "+found.size()+" извори поврзани со моделот. Подолу се достапните податоци и линкови за проверка.");
        List<GoogleModelResearch.Fact> values=GoogleModelResearch.extract(model,found);
        if(values.size()<3){
            googleHasResults=false;
            googleFacts.setText("");
            googleSources.removeAllViews();
            googleStatus.setText("Само "+values.size()+" потврден(и) податок(ци). "
                +"Не е доволно за корисен опис; ќе пробам други независни извори.");
            return;
        }
        bestFactCount=Math.max(bestFactCount,values.size());
        if(model.equals(compareModel))compareFacts=new ArrayList<>(values);
        googleHasResults=true;
        GoogleModelResearch.Result description=GoogleModelResearch.description(model,found);
        StringBuilder summary=new StringBuilder();
        if(description!=null){
            Uri uri=Uri.parse(description.url);
            String host=uri.getHost()==null?"извор":uri.getHost();
            String excerpt=description.snippet.replaceAll("\\s+"," ").trim();
            if(excerpt.length()>240)excerpt=excerpt.substring(0,240).trim()+"…";
            summary.append("КРАТОК ОПИС ОД ").append(provider.toUpperCase(Locale.ROOT))
                .append(" [").append(host).append("]\\n").append(excerpt).append("\\n\\n");
        }
        if(!values.isEmpty()){
            summary.append("НАЈВАЖНИ СПЕЦИФИКАЦИИ\\n");
            for(GoogleModelResearch.Fact fact:values){
                Uri source=Uri.parse(fact.url);
                summary.append("• ").append(fact.label).append(": ").append(fact.value)
                    .append(" [").append(source.getHost()).append("]\\n");
            }
            summary.append("\\n");
        }
        if(summary.length()==0)
            summary.append("Има извори за моделот, но тие не дадоа проверлив опис или спецификации во кратките извадоци. Отвори извор подолу за детали.");
        else
            summary.append("Описи и вредности се од наведените извори, не од Setec. За важна одлука провери ги кај производителот.");
        googleFacts.setText(summary.toString().replace("\\n","\n"));
        int limit=Math.min(found.size(),5);
        for(int i=0;i<limit;i++){
            GoogleModelResearch.Result hit=found.get(i);
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(11),dp(12),dp(12));
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(9));
            bg.setStroke(dp(1),0xFFE0E5EC);
            card.setBackground(bg);
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);
            layout.topMargin=dp(7);
            googleSources.addView(card,layout);
            card.addView(label(hit.title,13,true,INK));
            if(!hit.snippet.isEmpty())card.addView(label(hit.snippet,12,false,0xFF596574));
            Uri uri=Uri.parse(hit.url);
            card.addView(label((uri.getHost()==null?"Извор":uri.getHost())+" ↗",11,true,0xFFDE741D));
            card.setOnClickListener(v->openUri(hit.url));
        }
    }


    /**
     * In comparison mode, a SKU from the existing numeric scanner must first
     * resolve to an exact Setec product. We NEVER send an SKU to Google as if
     * it were a model and NEVER infer identity from the closest catalog title.
     */
    private void startCompareSkuLookup(String sku){
        if(!comparePicker||sku==null||!sku.matches("[0-9]{4,8}")){
            setStatus("Скенирај SETEC шифра од 4 до 8 цифри.");
            return;
        }
        compareSkuRequested=sku;
        clearComparisonData("");
        modelInput.setText(sku);
        modelInput.setEnabled(false);
        facts.setText("");
        googleFacts.setText("");
        googleSources.removeAllViews();
        sources.removeAllViews();
        setecOptions.removeAllViews();
        compareSkuCandidateUrls.clear();
        compareSkuCandidateIndex=0;
        compareSkuSearchFallback=false;
        bestSetecUrl="";
        setecMatched=false;
        setecStage=5;
        final int serial=++setecSerial;
        final int generation=++lookupGeneration;
        setecStatus.setText("🔎 Барам SETEC шифра "+sku+" — само бројки.");
        internetStatus.setText("Прво ја потврдувам шифрата на Setec.mk, па ги читам описот и техничките податоци од истиот производ.");
        setecBrowser.stopLoading();
        setecBrowser.loadUrl("https://www.setec.mk/");
        handler.postDelayed(()->{
            if(serial==setecSerial&&generation==lookupGeneration&&setecStage==5){
                loadCompareSkuFallback(serial);
            }
        },5200);
    }

    private void submitCompareSkuSearch(int serial,int generation){
        if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=5||setecBrowser==null)return;
        String query=JSONObject.quote(compareSkuRequested);
        String js="(function(){"+
            "let input=[...document.querySelectorAll('input')].find(x=>/пребарај|search/i.test(x.placeholder||'')||x.type==='search');"+
            "if(!input)return 'no-input';"+
            "let setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(input,"+query+");"+
            "input.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:"+query+"}));"+
            "input.dispatchEvent(new Event('change',{bubbles:true}));"+
            "let f=input.closest('form');"+
            "if(f){try{f.requestSubmit()}catch(e){}}else{let b=input.parentElement&&input.parentElement.querySelector('button');if(b)b.click();else input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));}"+
            "return 'submitted';})()";
        setecBrowser.evaluateJavascript(js,encoded->{
            if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=5)return;
            if("submitted".equals(decodeJs(encoded))){
                setecStage=6;
                setecStatus.setText("🔎 Пребарувам производ со шифра "+compareSkuRequested+" на Setec.mk…");
                for(long delay:new long[]{900,1900,3500,5700,8000})
                    handler.postDelayed(()->readCompareSkuMatches(serial,generation),delay);
                handler.postDelayed(()->{
                    if(serial==setecSerial&&generation==lookupGeneration&&setecStage==6)
                        loadCompareSkuFallback(serial);
                },8500);
            }
        });
    }

    private void loadCompareSkuFallback(int serial){
        if(serial!=setecSerial||setecMatched||setecBrowser==null||compareSkuSearchFallback)return;
        compareSkuSearchFallback=true;
        setecStage=6;
        final int generation=++lookupGeneration;
        setecStatus.setText("🔎 Повторно барам по точната SETEC шифра "+compareSkuRequested+"…");
        Uri url=Uri.parse("https://www.setec.mk/search-result").buildUpon()
            .appendQueryParameter("q",compareSkuRequested).build();
        setecBrowser.stopLoading();
        setecBrowser.loadUrl(url.toString());
        for(long delay:new long[]{1300,2700,4200,6500,9000})
            handler.postDelayed(()->readCompareSkuMatches(serial,generation),delay);
        handler.postDelayed(()->{
            if(serial==setecSerial&&generation==lookupGeneration&&setecStage==6){
                setecStatus.setText("Не ја најдов точно шифрата "+compareSkuRequested+
                    " во каталогот. Не избрав погрешен модел. Врати се и скенирај ја шифрата повторно.");
            }
        },10300);
    }

    private void readCompareSkuMatches(int serial,int generation){
        if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=6||setecBrowser==null)return;
        String script="(function(){"+
            "try{const q="+JSONObject.quote(compareSkuRequested)+";let seen=new Set(),out=[];"+
            "for(const a of document.querySelectorAll('a[href*=\"/products/\"]')){"+
            "let url;try{url=new URL(a.getAttribute('href'),location.href)}catch(e){continue}"+
            "if(!['www.setec.mk','setec.mk','set.mk','www.set.mk'].includes(url.hostname)||seen.has(url.href))continue;"+
            "let card=a.closest('article,li,[class*=\"product\"],[class*=\"Product\"],[class*=\"card\"],[class*=\"Card\"]')||a.parentElement||a;"+
            "let text=(card.innerText||'').replace(/\\s+/g,' ');"+
            "let m=text.match(/(?:Шифра|SKU)\\s*:?\\s*(\\d{4,8})/i);"+
            "let code=m?m[1]:'';"+
            "if(code!==q && !(new RegExp('(?:^|\\\\D)'+q+'(?:\\\\D|$)')).test(text))continue;"+
            "seen.add(url.href);out.push({url:url.href,sku:code});if(out.length>=16)break;}"+
            "return JSON.stringify(out);}catch(e){return '[]';}})()";
        setecBrowser.evaluateJavascript(script,encoded->{
            if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=6)return;
            try{
                String raw=decodeJs(encoded);
                if(raw==null)return;
                JSONArray list=new JSONArray(raw);
                for(int i=0;i<list.length();i++){
                    JSONObject item=list.optJSONObject(i);
                    if(item==null)continue;
                    String url=item.optString("url");
                    if(isSetecProduct(url)&&!compareSkuCandidateUrls.contains(url))
                        compareSkuCandidateUrls.add(url);
                }
                if(!compareSkuCandidateUrls.isEmpty())tryNextCompareSkuCandidate(serial);
            }catch(Exception ignored){}
        });
    }

    private void tryNextCompareSkuCandidate(int serial){
        if(serial!=setecSerial||setecMatched||setecBrowser==null)return;
        if(compareSkuCandidateIndex>=compareSkuCandidateUrls.size()){
            if(!compareSkuSearchFallback)loadCompareSkuFallback(serial);
            else{
                setecStage=0;
                setecStatus.setText("Ниту еден производ не ја потврди шифрата "+compareSkuRequested+
                    ". Пробај повторно или провери ја шифрата.");
            }
            return;
        }
        final String candidate=compareSkuCandidateUrls.get(compareSkuCandidateIndex++);
        final int generation=++lookupGeneration;
        setecStage=7;
        setecStatus.setText("🔎 Проверувам дали страницата ја има баш шифрата "+compareSkuRequested+"…");
        setecBrowser.stopLoading();
        setecBrowser.loadUrl(candidate);
        for(long delay:new long[]{1000,2300,3750})
            handler.postDelayed(()->readCompareSkuProduct(serial,generation),delay);
        handler.postDelayed(()->{
            if(serial==setecSerial&&generation==lookupGeneration&&setecStage==7&&!setecMatched)
                tryNextCompareSkuCandidate(serial);
        },5250);
    }

    private void readCompareSkuProduct(int serial,int generation){
        if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=7||setecBrowser==null)return;
        setecBrowser.evaluateJavascript(SetecPageData.READ_PRODUCT_SCRIPT,encoded->{
            if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=7||setecMatched)return;
            try{
                String raw=decodeJs(encoded);
                if(raw==null)return;
                JSONObject p=new JSONObject(raw);
                String sku=p.optString("sku");
                String fullTitle=p.optString("name");
                String cleanModel=SetecProductTitle.modelFromProductName(fullTitle);
                String url=setecBrowser.getUrl();
                if(!compareSkuRequested.equals(sku)||cleanModel.length()<5||
                   !SetecPageData.isOfficialProductUrl(url))return;
                compareSku=sku;
                compareClub=p.optString("club");
                compareRegular=p.optString("regular");
                bestSetecUrl=url;
                activeSetecModel=cleanModel;
                compareModel=cleanModel;
                compareFacts=new ArrayList<>();
                compareResearchBusy=true;
                setecMatched=true;
                setecStage=3;
                modelInput.setText(cleanModel);
                researchButton.setEnabled(false);
                StringBuilder details=new StringBuilder("SETEC.MK · МОДЕЛ, ШИФРА И ЦЕНА\n"+
                    cleanModel+"\nSetec шифра: "+sku);
                if(!compareClub.isEmpty())details.append("\nКлуб цена: ").append(compareClub).append(" ден.");
                if(!compareRegular.isEmpty())details.append("\nРедовна цена: ").append(compareRegular).append(" ден.");
                facts.setText(details.toString());
                setecStatus.setText("✓ Потврдена SETEC шифра "+sku+" и модел "+cleanModel+".");
                googleStatus.setText("Читам краток опис и технички спецификации од Setec.mk…");
                showSetecSpecs(cleanModel,url,p.optString("specs"),
                    p.optString("short"),fullTitle,p.optString("category"));
                // Setec can populate the technical tab a few seconds later.
                // Keep retrying for more details even if the H1 already gave 1 fact.
                for(long delay:new long[]{850,1800,3100,4700,6200})
                    handler.postDelayed(()->readCompareSkuSpecs(serial,generation),delay);
                handler.postDelayed(()->{
                    if(serial!=setecSerial||generation!=lookupGeneration||
                       !comparePicker||!compareSkuRequested.equals(compareSku))return;
                    compareResearchBusy=false;
                    if(compareFacts.isEmpty()){
                        googleStatus.setText("Setec.mk нема доволно јасни технички податоци за овој производ.");
                        if(googleFacts.getText().length()==0)
                            googleFacts.setText("Моделот и цената се потврдени, но недостига сигурен опис. "+
                                "Отвори го производот на Setec.mk за проверка.");
                    }else{
                        googleStatus.setText("✓ Извлечени "+compareFacts.size()+
                            " најважни технички податоци од истиот Setec производ.");
                    }
                },7000);
            }catch(Exception ignored){}
        });
    }

    private void readCompareSkuSpecs(int serial,int generation){
        if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=3||
           !comparePicker||setecBrowser==null||compareFacts.size()>=5)return;
        setecBrowser.evaluateJavascript(SetecPageData.READ_PRODUCT_SCRIPT,encoded->{
            if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=3||
               !compareSkuRequested.equals(compareSku))return;
            try{
                String raw=decodeJs(encoded);
                if(raw==null)return;
                JSONObject p=new JSONObject(raw);
                if(!compareSkuRequested.equals(p.optString("sku")))return;
                if(!bestSetecUrl.equals(setecBrowser.getUrl()))return;
                showSetecSpecs(compareModel,bestSetecUrl,p.optString("specs"),
                    p.optString("short"),p.optString("name"),p.optString("category"));
            }catch(Exception ignored){}
        });
    }

    private void showSetecSpecs(String model,String url,String technical,
                                String shortDescription,String fullTitle,String category){
        if(!SetecPageData.isOfficialProductUrl(url))return;
        List<GoogleModelResearch.Fact> chosen=SetecKeySpecs.extract(
            model,url,technical,shortDescription,fullTitle,category);
        if(chosen.isEmpty()){
            if(googleFacts.getText().length()==0){
                googleStatus.setText("Се вчитуваат спецификациите од Setec.mk…");
                String shortText=shortDescription==null?"":shortDescription
                    .replaceAll("[\r\n\t]+"," ").replaceAll("\\s+"," ").trim();
                if(shortText.length()>20&&shortText.length()<2500&&
                   !shortText.matches("(?iu).*(?:клуб цена|редовна цена|кошничка|рати).*")){
                    if(shortText.length()>300)shortText=shortText.substring(0,300).trim()+"…";
                    googleFacts.setText("КРАТОК ОПИС ОД SETEC.MK\n"+shortText+
                        "\n\nТехничките вредности се проверуваат одделно.");
                }
            }
            return;
        }
        if(comparePicker&&model.equals(compareModel)){
            if(chosen.size()<compareFacts.size())return;
            compareFacts=new ArrayList<>(chosen);
            if(chosen.size()>=5)compareResearchBusy=false;
        }
        StringBuilder summary=new StringBuilder("НАЈВАЖНИ СПЕЦИФИКАЦИИ ОД SETEC.MK\n");
        for(GoogleModelResearch.Fact fact:chosen)
            summary.append("• ").append(fact.label).append(": ").append(fact.value).append("\n");
        googleFacts.setText(summary.toString());
        googleStatus.setText("✓ Пронајдени "+chosen.size()+
            " важни технички податоци. Се прикажуваат само вредности од Setec.mk.");
        internetStatus.setText("Извор: истиот проверен производ на Setec.mk. "+
            "Нејасните вредности се изоставени.");
        googleSources.removeAllViews();
    }

    // One search per chosen model. Research stays inside this Activity.
    private void startSetecLookup(String requestedModel){
        bestSetecUrl="";
        setecOptions.removeAllViews();
        activeSetecModel=ModelNameParser.normalizeModel(requestedModel);
        int serial=++setecSerial;
        setecMatched=false;homeSearchFallback=false;homeSearchSubmitted=false;
        setecQueries=SetecModelMatcher.searchTerms(activeSetecModel);
        setecQueryIndex=0;slugHostIndex=0;lookupGeneration++;
        setecSpecAttempts=0;
        officialSlug=SetecModelMatcher.possibleOfficialSlug(activeSetecModel);
        if(!officialSlug.isEmpty()){
            // A guessed slug is ONLY a lookup candidate. It is never displayed as
            // verified until the product page h1 matches the full scanned model.
            trySetecSlug(serial);
        }else tryNextSetecSearch(serial);
    }

    private void trySetecSlug(int serial){
        if(serial!=setecSerial||setecMatched)return;
        if(officialSlug.isEmpty()||slugHostIndex>=2){
            tryNextSetecSearch(serial);return;
        }
        int generation=++lookupGeneration;
        setecStage=4;
        String host=slugHostIndex++==0?"setec.mk":"set.mk";
        setecStatus.setText("🔎 Проверувам точна ознака на "+host+"…");
        setecBrowser.stopLoading();
        setecBrowser.loadUrl("https://"+host+"/products/"+officialSlug);
        handler.postDelayed(()->{
            if(serial==setecSerial&&setecStage==4&&generation==lookupGeneration&&!setecMatched)
                trySetecSlug(serial);
        },4600);
    }

    private void tryNextSetecSearch(int serial){
        if(serial!=setecSerial||setecMatched)return;
        if(setecQueryIndex>=Math.min(4,setecQueries.size())){
            tryHomeSearch(serial);return;
        }
        String term=setecQueries.get(setecQueryIndex++);
        int generation=++lookupGeneration;
        setecStage=1;
        setecStatus.setText("🔎 Пребарувам Setec по модел: "+term);
        Uri target=Uri.parse("https://www.setec.mk/search-result").buildUpon()
            .appendQueryParameter("q",term).build();
        setecBrowser.stopLoading();
        setecBrowser.loadUrl(target.toString());
        handler.postDelayed(()->{
            if(serial==setecSerial&&setecStage==1&&generation==lookupGeneration&&!setecMatched){
                tryNextSetecSearch(serial);
            }
        },4900);
    }

    private void tryHomeSearch(int serial){
        if(serial!=setecSerial||setecMatched||homeSearchFallback)return;
        int generation=++lookupGeneration;
        setecStage=1;homeSearchFallback=true;homeSearchSubmitted=false;
        setecStatus.setText("🔎 Пробувам и преку пребарувањето на официјалната Setec страница…");
        setecBrowser.stopLoading();
        setecBrowser.loadUrl("https://www.setec.mk/");
        handler.postDelayed(()->{
            if(serial==setecSerial&&setecStage==1&&generation==lookupGeneration&&!setecMatched){
                setecStatus.setText("Не најдов доволно сигурен Setec производ. Провери ја шифрата или пробај повторно.");
            }
        },9500);
    }

    private void readDirectSetecModel(int serial,int generation){
        if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=4||setecMatched)return;
        String js="(function(){return document.querySelector('h1')?.innerText||''})()";
        setecBrowser.evaluateJavascript(js,result->{
            if(serial!=setecSerial||generation!=lookupGeneration||setecStage!=4||setecMatched)return;
            String name=decodeJs(result);
            if(name==null||SetecModelMatcher.score(activeSetecModel,name)<100)return;
            String actualUrl=setecBrowser.getUrl();
            if(!isSetecProduct(actualUrl))return;
            setecMatched=true;setecStage=2;bestSetecUrl=actualUrl;
            setecStatus.setText("✓ Проверен модел: "+name.trim()+"\nГи читам шифрата и цените…");
            for(long delay:new long[]{100,800,1650,3200})
                handler.postDelayed(()->readSetecDetails(serial),delay);
        });
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
        String js="(function(){\n try{\n  let result=new Map();\n  for(const a of document.querySelectorAll('a[href*=\"/products/\"]')){\n   let u;try{u=new URL(a.getAttribute('href'),location.href)}catch(e){continue}\n   if(!['www.setec.mk','setec.mk','set.mk','www.set.mk'].includes(u.hostname)||result.has(u.href))continue;\n   let card=a.closest('article,li,[class*=\"product\"],[class*=\"Product\"],[class*=\"card\"],[class*=\"Card\"]')||a.parentElement||a;\n   let heading=card.querySelector('h2,h3,h4,[class*=\"title\"],[class*=\"name\"]');\n   let name=(heading?.innerText||a.title||a.innerText||'').replace(/\\s+/g,' ').trim();\n   if(name.length<5||name.length>210)continue;\n   result.set(u.href,{url:u.href,name:name});\n   if(result.size>=100)break;\n  }\n  return JSON.stringify([...result.values()]);\n }catch(e){return '[]'}\n})()";
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
                    setecMatched=true; // Pause navigation until the user selects the exact variant.
                    setecOptions.removeAllViews();
                    setecStatus.setText("Пронајдов слични производи. Избери го точниот модел/боја за да ги видиш цените и шифрата:");
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
        setecStatus.setText("✓ Точен производ: "+name+"\nГи читам шифрата и цените од Setec.mk…");
        setecBrowser.loadUrl(url);
    }

    private static boolean isSetecProduct(String link){
        try{
            Uri u=Uri.parse(link);
            return "https".equalsIgnoreCase(u.getScheme())
                &&("setec.mk".equalsIgnoreCase(u.getHost())||"www.setec.mk".equalsIgnoreCase(u.getHost())||"set.mk".equalsIgnoreCase(u.getHost())||"www.set.mk".equalsIgnoreCase(u.getHost()))
                &&u.getPath()!=null&&u.getPath().startsWith("/products/");
        }catch(Exception e){return false;}
    }

    private void readSetecDetails(int serial){
        if(serial!=setecSerial||(setecStage!=2&&setecStage!=3)||
            setecBrowser==null||comparePicker)return;
        setecBrowser.evaluateJavascript(SetecPageData.READ_PRODUCT_SCRIPT,encoded->{
            if(serial!=setecSerial||(setecStage!=2&&setecStage!=3))return;
            try{
                String raw=decodeJs(encoded);
                if(raw==null)return;
                JSONObject product=new JSONObject(raw);
                String name=SetecProductTitle.modelFromProductName(product.optString("name"));
                if(SetecModelMatcher.score(activeSetecModel,name)<100)return;
                String url=setecBrowser.getUrl();
                if(!SetecPageData.isOfficialProductUrl(url))return;
                String code=product.optString("sku");
                String club=product.optString("club");
                String regular=product.optString("regular");
                bestSetecUrl=url;
                StringBuilder lines=new StringBuilder("SETEC.MK · МОДЕЛ, ШИФРА И ЦЕНА\n");
                lines.append(name);
                lines.append("\nSetec шифра: ").append(code.isEmpty()?"не е пронајдена":code);
                lines.append("\nКлуб цена: ").append(club.isEmpty()?"не е пронајдена":club+" ден.");
                lines.append("\nРедовна цена: ").append(regular.isEmpty()?"не е пронајдена":regular+" ден.");
                facts.setText(lines.toString());
                if(!code.isEmpty()&&(!club.isEmpty()||!regular.isEmpty())){
                    setecStage=3;
                    setecStatus.setText("✓ Модел, шифра и цена од потврден Setec производ.");
                }else{
                    setecStatus.setText("✓ Точниот модел е пронајден. Се проверуваат шифрата и цените.");
                }
                showSetecSpecs(name,url,product.optString("specs"),
                    product.optString("short"),product.optString("name"),
                    product.optString("category"));
                if(setecSpecAttempts++<5){
                    handler.postDelayed(()->readSetecDetails(serial),1000);
                }else if(googleFacts.getText().length()==0){
                    googleStatus.setText("Setec.mk не прикажа сигурни технички податоци за овој модел.");
                }
            }catch(Exception ignored){}
        });
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
        String code=SetecModelMatcher.modelCode(model);
        String q=code.length()>=5?"\""+code+"\" "+model.split("\\s+")[0]+" specifications":GoogleModelResearch.searchQuery(model);
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
        if(googleHasResults)return;
        // Never display raw fallback results. Public search providers sometimes
        // return completely unrelated TikTok/Reddit links for exact model queries.
        List<GoogleModelResearch.Result> verified=new ArrayList<>();
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(Result r:found){
            if(!seen.add(r.url))continue;
            GoogleModelResearch.Result candidate=new GoogleModelResearch.Result(r.title,r.description,r.url);
            if(GoogleModelResearch.relevant(model,candidate))verified.add(candidate);
        }
        if(GoogleModelResearch.extract(model,verified).size()<3){
            internetStatus.setText("Нема потврдени интернет резултати за овој точен модел. Нема да прикажувам нерелевантни линкови.");
            googleStatus.setText("Google не дозволи автоматско читање, а резервниот извор нема потврдени податоци. Користи „Веб резултати“ за рачна проверка.");
            if(!error.isEmpty())sources.addView(label(error,12,false,0xFF915F28));
            return;
        }
        googleHasResults=true;
        internetStatus.setText("Пронајдени "+verified.size()+" проверени извори за моделот.");
        showGoogleSources(model,verified,"Проверени јавни извори");
    }
    private void openSearch(String raw,String prefix){
        String value=raw==null?"":raw.trim();
        if(value.length()<3){setStatus("Prvo skeniraj ili vnesi model.");return;}
        String query=prefix+GoogleModelResearch.searchQuery(value);
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
        researchSerial++;setecSerial++;handler.removeCallbacksAndMessages(null);
        if(setecBrowser!=null){setecBrowser.stopLoading();setecBrowser.destroy();setecBrowser=null;}
        if(googleBrowser!=null){googleBrowser.stopLoading();googleBrowser.destroy();googleBrowser=null;}
        if(cameraProvider!=null)cameraProvider.unbindAll();
        recognizer.close();
        cameraExecutor.shutdownNow();networkExecutor.shutdownNow();
        super.onDestroy();
    }
}
